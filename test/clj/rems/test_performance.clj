(ns rems.test-performance
  (:require [clj-memory-meter.core :as mm]
            [criterium.core :as criterium]
            [medley.core :refer [map-vals]]
            [mount.core :as mount]
            [rems.api.applications-v2 :as applications-v2]
            [rems.api.applications :as applications-api]
            [rems.db.applications :as applications])
  (:import [java.util Locale]))

(defn run-benchmark [benchmark]
  (println "\n=====" (:name benchmark) "=====")
  (when-let [setup (:setup benchmark)]
    (setup))
  (let [result (criterium/with-progress-reporting
                 (criterium/quick-benchmark ((:benchmark benchmark)) {}))]
    (criterium/report-result result)
    ;(clojure.pprint/pprint (dissoc result :results))
    {:name (:name benchmark)
     :mean (first (:mean result))
     :high (first (:upper-q result))
     :low (first (:lower-q result))}))

(defn run-benchmarks [benchmarks]
  (let [results (doall (for [benchmark benchmarks]
                         (run-benchmark benchmark)))
        longest-name (->> results (map :name) (map count) (apply max))
        right-pad (fn [s length]
                    (apply str s (repeat (- length (count s)) " ")))]
    (println "\n===== Summary =====")
    (doseq [result results]
      (println (right-pad (:name result) longest-name)
               (->> (select-keys result [:low :mean :high])
                    (map-vals #(String/format Locale/ENGLISH "%.3f ms" (to-array [(* 1000 %)]))))))))

(defn benchmark-get-events []
  (let [last-event-id (:event/id (last (applications/get-all-events-since 0)))
        test-get-all-events-since-beginning #(doall (applications/get-all-events-since 0))
        test-get-all-events-since-end #(doall (applications/get-all-events-since last-event-id))
        test-get-application-events #(doall (applications/get-application-events 12))]
    (run-benchmarks [{:name "get-all-events-since, all events"
                      :benchmark test-get-all-events-since-beginning}
                     {:name "get-all-events-since, zero new events"
                      :benchmark test-get-all-events-since-end}
                     {:name "get-application-events"
                      :benchmark test-get-application-events}])))

(defn benchmark-get-all-applications []
  (let [test-get-all-unrestricted-applications #(doall (applications-v2/get-all-unrestricted-applications))
        test-get-all-applications #(doall (applications-v2/get-all-applications "alice"))
        test-get-all-application-roles #(doall (applications-v2/get-all-application-roles "developer"))
        test-get-my-applications #(doall (applications-v2/get-my-applications "alice"))
        ;; developer can view much more applications than alice, so it takes longer to filter reviews from all apps
        test-get-todos #(doall (applications-api/get-todos "developer"))
        no-cache (fn []
                   (mount/stop #'applications-v2/all-applications-cache))
        cached (fn []
                 (mount/stop #'applications-v2/all-applications-cache)
                 (mount/start #'applications-v2/all-applications-cache)
                 (test-get-all-unrestricted-applications))]
    (run-benchmarks [{:name "get-all-unrestricted-applications, no cache"
                      :benchmark test-get-all-unrestricted-applications
                      :setup no-cache}
                     {:name "get-all-unrestricted-applications, cached"
                      :benchmark test-get-all-unrestricted-applications
                      :setup cached}
                     {:name "get-all-applications, cached"
                      :benchmark test-get-all-applications
                      :setup cached}
                     {:name "get-all-application-roles, cached"
                      :benchmark test-get-all-application-roles
                      :setup cached}
                     {:name "get-my-applications, cached"
                      :benchmark test-get-my-applications
                      :setup cached}
                     {:name "get-todos, cached"
                      :benchmark test-get-todos
                      :setup cached}])
    (println "cache size" (mm/measure applications-v2/all-applications-cache))))

(defn benchmark-get-application []
  (let [test-get-application #(applications-v2/get-application "developer" 12)
        no-cache (fn []
                   (mount/stop #'applications-v2/application-cache))
        cached (fn []
                 (mount/stop #'applications-v2/application-cache)
                 (mount/start #'applications-v2/application-cache)
                 (test-get-application))]
    (run-benchmarks [{:name "get-application, no cache"
                      :benchmark test-get-application
                      :setup no-cache}
                     {:name "get-application, cached"
                      :benchmark test-get-application
                      :setup cached}])
    (println "cache size" (mm/measure applications-v2/application-cache))))

(comment
  ;; Note: If clj-memory-meter throws InaccessibleObjectException on Java 9+,
  ;;       you *could* work around it by adding `--add-opens` JVM options, but
  ;;       the root cause is probably that there is a lazy sequence that could
  ;;       easily be avoided.
  (benchmark-get-events)
  (benchmark-get-all-applications)
  (benchmark-get-application))
