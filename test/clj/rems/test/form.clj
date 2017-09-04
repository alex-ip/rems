(ns rems.test.form
  (:require [clojure.test :refer :all]
            [hiccup-find.core :refer :all]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.form :as form]
            [rems.test.tempura :refer [with-fake-tempura]]
            [ring.mock.request :refer :all]))

(def field #'form/field)

(deftest test-license-field
  (let [f (field {:type "license" :licensetype "link" :textcontent "ab.c" :title "Link to license"})
        [[_ attrs]] (hiccup-find [:input] f)
        [[_ target]] (hiccup-find [:a] f)]
    (is (= "checkbox" (:type attrs))
        "Checkbox exists for supported license type")
    (is (= "_blank" (:target target))
        "License with type link opens to a separate tab"))
  (let [f (field {:type "license" :licensetype "attachment" :textcontent "ab.c" :title "Link to license"})]
    (is (.contains (hiccup-text f) "Unsupported field ")
        "Unsupported license type gives a warning")))

(def validate #'form/validate)

(deftest test-validate
  (with-fake-tempura
    (is (= :valid (validate
                   {:items [{:title "A"
                             :optional true
                             :value nil}
                            {:title "B"
                             :optional false
                             :value "xyz"}
                            {:title "C"
                             :optional false
                             :value "1"}]})))

    (is (not= :valid (validate
                      {:items [{:title "A"
                                :optional false
                                :value "a"}]
                       :licenses [{:title "LGPL"}]})))

    (is (= :valid (validate
                   {:items [{:title "A"
                             :optional false
                             :value "a"}]
                    :licenses [{:title "LGPL"
                                :approved true}]})))

    (let [res (validate
               {:items [{:title "A"
                         :optional true
                         :value nil}
                        {:title "B"
                         :optional false
                         :value ""}
                        {:title "C"
                         :optional false
                         :value nil}]})]
      (testing res
        (is (vector? res))
        (is (= 2 (count res)))
        (is (.contains (first res) "B"))
        (is (.contains (second res) "C"))))))

(deftest test-save
  (with-fake-tempura
    ;; simple mock db
    (let [world (atom {:submitted #{}})
          run (fn [path params]
                (form/form-routes
                 (assoc (request :post path)
                        :form-params params)))]
      (with-redefs
        [rems.db.applications/get-form-for
         (fn [_ & [application]]
           {:id 137
            :application application
            :state (get-in @world [:states application])
            :items [{:id 61
                     :title "A"
                     :type "text"
                     :optional true
                     :value (get-in @world [:values application 61])}
                    {:id 62
                     :title "B"
                     :type "text"
                     :optional false
                     :value (get-in @world [:values application 62])}]
            :licenses [{:id 70
                        :type "license"
                        :licensetype "link"
                        :title "KielipankkiTerms"
                        :textcontent "https://kitwiki.csc.fi/twiki/bin/view/FinCLARIN/KielipankkiTerms"
                        :approved (get-in @world [:approvals application 70])}]})

         db/save-field-value!
         (fn [{application :application
               item :item
               value :value}]
           (swap! world assoc-in [:values application item] value))

         db/save-license-approval!
         (fn [{application :catappid
               licid :licid
               state :state}]
           (swap! world assoc-in [:approvals application licid] state))

         db/delete-license-approval!
         (fn [{application :catappid
               licid :licid}]
           (swap! world dissoc :approvals))

         db/create-application!
         (constantly {:id 2})

         applications/submit-application
         (fn [application-id]
           (swap! world update :submitted conj application-id))]

        (testing "first save"
          (let [resp (run "/form/7/save" {"field61" "x"
                                          "field62" "y"
                                          "license70" "approved"})
                flash (first (:flash resp))
                flash-text (hiccup-text (:contents flash))]
            (is (= 303 (:status resp)))
            (testing flash
              (is (= :success (:status flash)))
              (is (.contains flash-text "saved")))
            (is (= {:submitted #{} :values {2 {61 "x", 62 "y"}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "second save, with missing optional field. shouldn't create new draft"
          (let [resp (run "/form/7/save" {"field61" ""
                                          "field62" "z"
                                          "license70" "approved"})
                flash (first (:flash resp))
                flash-text (hiccup-text (:contents flash))]
            (is (= 303 (:status resp)))
            (testing flash
              (is (= :success (:status flash)))
              (is (.contains flash-text "saved")))
            (is (= {:submitted #{} :values {2 {61 "", 62 "z"}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "save with unchecked license"
          (let [resp (run "/form/7/save" {"field61" "x"
                                          "field62" "y"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (is (= 303 (:status resp)))
            (testing flash1
              (is (= :success (:status flash1)))
              (is (.contains flash1-text "saved")))
            (testing flash2
              (is (= :info (:status flash2)))
              (is (.contains flash2-text "\"KielipankkiTerms\"")))
            (is (= {:submitted #{} :values {2 {61 "x", 62 "y"}}}
                   @world))))

        (testing "save with missing mandatory field"
          (let [resp (run "/form/7/2/save" {"field61" "w"
                                            "field62" ""
                                            "license70" "approved"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (testing flash1
              (is (= :success (:status flash1)))
              (is (.contains flash1-text "saved")))
            (testing flash2
              (is (= :info (:status flash2)))
              (is (.contains flash2-text "\"B\"")))
            (is (= {:submitted #{} :values {2 {61 "w", 62 ""}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "submit with missing mandatory field"
          (let [resp (run "/form/7/2/save" {"field61" "u"
                                            "field62" ""
                                            "license70" "approved"
                                            "submit" "true"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (testing flash1
              (is (= :warning (:status flash1)))
              (is (.contains flash1-text "saved"))
              (is (not (.contains flash1-text "submitted"))))
            (testing flash2
              (is (= :warning (:status flash2)))
              (is (.contains flash2-text "\"B\"")))
            (is (= {:submitted #{} :values {2 {61 "u", 62 ""}} :approvals {2 {70 "approved"}}}
                   @world))))

        (testing "submit with unchecked license"
          (let [resp (run "/form/7/2/save" {"field61" ""
                                            "field62" "v"
                                            "submit" "true"})
                [flash1 flash2] (:flash resp)
                flash1-text (hiccup-text (:contents flash1))
                flash2-text (hiccup-text (:contents flash2))]
            (testing flash1
              (is (= :warning (:status flash1)))
              (is (.contains flash1-text "saved"))
              (is (not (.contains flash1-text "submitted"))))
            (testing flash2
              (is (= :warning (:status flash2)))
              (is (.contains flash2-text "\"KielipankkiTerms\"")))
            (is (= {:submitted #{} :values {2 {61 "", 62 "v"}}}
                   @world))))

        (testing "successful submit"
          (let [resp (run "/form/7/2/save" {"field61" ""
                                            "field62" "v"
                                            "license70" "approved"
                                            "submit" "true"})
                flash (first (:flash resp))
                flash-text (hiccup-text (:contents flash))]
            (testing flash
              (is (= :success (:status flash)))
              (is (not (.contains flash-text "saved")))
              (is (.contains flash-text "submitted")))
            (is (= {:submitted #{2} :values {2 {61 "", 62 "v"}} :approvals {2 {70 "approved"}}}
                   @world))))))))

(def form #'form/form)

(deftest test-editable
  (with-fake-tempura
    (binding [context/*active-role* :applicant]
      (let [readonly? (fn [[_tag attrs]]
                        (case (:type attrs)
                          "checkbox" (:disabled attrs) ;; checkboxes are special
                          (:readonly attrs)))
            all-inputs (fn [body] (remove #(= "comment" (:name (second %)))
                                          (concat (hiccup-find [:div.form-group :input] body)
                                                  (hiccup-find [:div.form-group :textarea] body))))
            submit-button #(first (hiccup-find [:.submit-button] %))
            children-of #(remove nil? (mapcat (partial drop 2) %))
            data {:items [{:type "text"}
                          {:type "texta"}]
                  :licenses [{:type "license" :licensetype "link"
                              :textcontent "" :title ""}]}]
        (testing "new form"
          (let [body (form data)]
            (is (= [false false false] (map readonly? (all-inputs body))))
            (is (submit-button body))))
        (testing "draft"
          (let [body (form (assoc data :application {:state "draft"}))]
            (is (= [false false false] (map readonly? (all-inputs body))))
            (is (submit-button body))))
        (doseq [state ["applied" "approved" "rejected"]]
          (testing state
            (let [body (form (assoc data :application {:state state}))]
              (is (= [true true true] (map readonly? (all-inputs body))))
              (is (nil? (submit-button body))))))
        (testing "sees events"
          (let [body (form (assoc data :application {:state "applied" :events [{:comment "hello"}]}))]
            (is (not-empty (children-of (hiccup-find [:#events] body))) "Should see collapsible events block")))))))

(defn- get-action-buttons [form-data]
  (hiccup-find [:.commands] (form form-data)))

(defn- action-button-check [emptyness-fn action-buttons msg]
  (is (emptyness-fn (hiccup-find [:button#close.btn.btn-secondary] action-buttons)) (str msg "close button"))
  (is (emptyness-fn (hiccup-find [:button#reject.btn.btn-secondary] action-buttons)) (str msg "reject button"))
  (is (emptyness-fn (hiccup-find [:button#return.btn.btn-secondary] action-buttons)) (str msg "return button"))
  (is (emptyness-fn (hiccup-find [:button#review-request.btn.btn-secondary] action-buttons)) (str msg "review request button"))
  (is (emptyness-fn (hiccup-find [:button#approve.btn.btn-primary] action-buttons)) (str msg "approve button")))

(defn- validate-approver-actions-absence [form-data]
  (action-button-check empty? (get-action-buttons form-data) "Should not see "))

(defn- validate-approver-actions-presence [form-data]
  (action-button-check not-empty (get-action-buttons form-data) "Should see "))

(defn- validate-review-actions-absence [form-data]
  (is (empty? (hiccup-find [:button#review.btn.btn-primary] (get-action-buttons form-data))) "should not see review button"))

(defn- validate-review-actions-presence [form-data]
  (is (not-empty (hiccup-find [:button#review.btn.btn-primary] (get-action-buttons form-data))) "should see review button"))

(defn- validate-back-button-absence [form-data]
  (is (empty? (hiccup-find [:a#back] (get-action-buttons form-data))) "should not see back button"))

(defn- validate-back-button-presence [form-data]
  (is (not-empty (hiccup-find [:a#back] (get-action-buttons form-data))) "should see back button"))

(deftest test-form-actions
  (with-fake-tempura
    (let [actionable-data {:application {:id 2
                                         :catid 2
                                         :applicantuserid "developer"
                                         :start nil
                                         :wfid 2
                                         :fnlround 1
                                         :state "applied"
                                         :curround 0
                                         :events
                                         [{:userid "developer"
                                           :round 0
                                           :event "apply"
                                           :comment nil
                                           :time nil}]}}
          unactionable-data {:application {:id 2
                                           :catid 2
                                           :applicantuserid "developer"
                                           :start nil
                                           :wfid 2
                                           :fnlround 0
                                           :state "approved"
                                           :curround 0
                                           :events
                                           [{:userid "developer"
                                             :round 0
                                             :event "apply"
                                             :comment nil
                                             :time nil}
                                            {:userid "bob"
                                             :round 0
                                             :event "approved"
                                             :comment nil
                                             :time nil}]}}]
      (with-redefs [rems.db.workflow-actors/get-by-role
                    (fn [appid round role]
                      (let [data [{:id 2 :actoruserid "carl" :role "reviewer" :round 0}
                                  {:id 2 :actoruserid "carl" :role "approver" :round 1}
                                  {:id 2 :actoruserid "bob" :role "approver" :round 0}
                                  {:id 2 :actoruserid "bob" :role "reviewer" :round 1}]]
                        (->> data
                             (filterv (fn [app] (and (= round (:round app)) (= role (:role app)))))
                             (map :actoruserid))))
                    rems.db.applications/get-application-state
                    (fn [_]
                      (:application actionable-data))]

        (testing "As an applicant"
          (testing "on an actionable form"
            (validate-back-button-absence actionable-data)
            (validate-approver-actions-absence actionable-data)
            (validate-review-actions-absence actionable-data))
          (testing "on an unactionable form"
            (validate-back-button-absence unactionable-data)
            (validate-approver-actions-absence unactionable-data)
            (validate-review-actions-absence unactionable-data)))
        (binding [context/*user* {"eppn" "bob"}
                  context/*active-role* :approver]
          (testing "As a current round approver"
            (testing "on an actionable form"
              (validate-back-button-presence actionable-data)
              (validate-approver-actions-presence actionable-data)
              (validate-review-actions-absence actionable-data)))
          (testing "As an approver"
            (testing "on an unactionable form"
              (validate-back-button-presence unactionable-data)
              (validate-approver-actions-absence unactionable-data)
              (validate-review-actions-absence unactionable-data))))
        (testing "As an approver, who is not set for the current round, on an actionable form"
          (binding [context/*user* {"eppn" "carl"}
                    context/*active-role* :approver]
            (validate-back-button-presence actionable-data)
            (validate-approver-actions-absence actionable-data)
            (validate-review-actions-absence actionable-data)))
        (testing "As a reviewer"
          (binding [context/*user* {"eppn" "carl"}
                    context/*active-role* :reviewer]
            (testing "on an actionable form"
              (validate-back-button-presence actionable-data)
              (validate-approver-actions-absence actionable-data)
              (validate-review-actions-presence actionable-data))
            (testing "on an unactionable form"
              (validate-back-button-presence unactionable-data)
              (validate-approver-actions-absence unactionable-data)
              (validate-review-actions-absence unactionable-data))))
        (testing "As a reviwer, who is not set for the current round, on an actionable form"
          (binding [context/*user* {"eppn" "bob"}
                    context/*active-role* :reviewer]
            (validate-back-button-presence actionable-data)
            (validate-approver-actions-absence actionable-data)
            (validate-review-actions-absence actionable-data)))))))
