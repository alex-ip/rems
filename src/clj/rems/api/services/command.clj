(ns rems.api.services.command
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [rems.application.approver-bot :as approver-bot]
            [rems.application.commands :as commands]
            [rems.db.applications :as applications]
            [rems.db.blacklist :as blacklist]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.events :as events]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.email.core :as email]
            [rems.form-validation :as form-validation]
            [rems.util :refer [secure-token]]))

(defn run-process-managers [new-events]
  ;; the copy-as-new command produces events for multiple applications, so there can be 1 or 2 app-ids
  (let [app-ids (->> new-events
                     (filter (fn [event]
                               ;; performance optimization: run only when an interesting event happens
                               ;; (reading the app from DB is slowish; consider an in-memory event-based solution instead)
                               (= :application.event/submitted (:event/type event))))
                     (map :application/id)
                     distinct)]
    (->> app-ids
         (map applications/get-unrestricted-application)
         (mapcat #(approver-bot/generate-commands %))
         doall)))

(def ^:private command-injections
  {:valid-user? users/user-exists?
   :validate-fields form-validation/validate-fields
   :secure-token secure-token
   :get-catalogue-item catalogue/get-localized-catalogue-item
   :get-catalogue-item-licenses applications/get-catalogue-item-licenses
   :get-workflow workflow/get-workflow
   :allocate-application-ids! applications/allocate-application-ids!
   :add-to-blacklist! blacklist/add-to-blacklist!})

(defn command! [cmd]
  ;; Use locks to prevent multiple commands being executed in parallel.
  ;; Serializable isolation level will already avoid anomalies, but produces
  ;; lots of transaction conflicts when there is contention. This lock
  ;; roughly doubles the throughput for rems.db.test-transactions tests.
  (jdbc/execute! db/*db* ["LOCK TABLE application_event IN SHARE ROW EXCLUSIVE MODE"])
  (let [app (when-let [app-id (:application-id cmd)]
              (applications/get-unrestricted-application app-id))
        result (commands/handle-command cmd app command-injections)]
    (when-not (:errors result)
      (doseq [event (:events result)]
        (events/add-event! event))
      (email/generate-emails! (:events result))
      (doseq [cmd2 (run-process-managers (:events result))]
        (let [result (command! cmd2)]
          (when (:errors result)
            (log/error "process manager command failed"
                       (pr-str {:cmd cmd2 :result result :parent-cmd cmd}))))))
    result))