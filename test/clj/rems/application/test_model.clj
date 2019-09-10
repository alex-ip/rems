(ns rems.application.test-model
  (:require [clojure.test :refer :all]
            [rems.api.schema :as schema]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.common-util :refer [deep-merge]]
            [rems.permissions :as permissions]
            [schema.core :as s])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

(def ^:private get-form-template
  {40 {:form/id 40
       :form/organization "org"
       :form/title "form title"
       :form/fields [{:field/id 41
                      :field/title {:en "en title" :fi "fi title"}
                      :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                      :field/optional false
                      :field/options []
                      :field/max-length 100
                      :field/type :description}
                     {:field/id 42
                      :field/title {:en "en title" :fi "fi title"}
                      :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                      :field/optional false
                      :field/options []
                      :field/max-length 100
                      :field/type :text}]
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}})

(def ^:private get-catalogue-item
  {10 {:id 10
       :resource-id 11
       :resid "urn:11"
       :wfid 50
       :formid 40
       :localizations {:en {:id 10
                            :langcode :en
                            :title "en title"
                            :infourl "http://info.com"}
                       :fi {:id 10
                            :langcode :fi
                            :title "fi title"
                            :infourl nil}}
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}
   20 {:id 20
       :resource-id 21
       :resid "urn:21"
       :wfid 50
       :formid 40
       :localizations {:en {:id 20
                            :langcode :en
                            :title "en title"
                            :infourl "http://info.com"}
                       :fi {:id 20
                            :langcode :fi
                            :title "fi title"
                            :infourl nil}}
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}
   30 {:id 30
       :resource-id 31
       :resid "urn:31"
       :wfid 50
       :formid 40
       :localizations {:en {:id 20
                            :langcode :en
                            :title "en title"
                            :infourl "http://info.com"}
                       :fi {:id 20
                            :langcode :fi
                            :title "fi title"
                            :infourl nil}}
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}})

(def ^:private get-license
  {30 {:id 30
       :licensetype "link"
       :localizations {:en {:title "en title"
                            :textcontent "http://en-license-link"}
                       :fi {:title "fi title"
                            :textcontent "http://fi-license-link"}}
       :enabled true
       :archived false}
   31 {:id 31
       :licensetype "text"
       :localizations {:en {:title "en title"
                            :textcontent "en license text"}
                       :fi {:title "fi title"
                            :textcontent "fi license text"}}
       :enabled true
       :archived false}
   32 {:id 32
       :licensetype "attachment"
       :localizations {:en {:title "en title"
                            :textcontent "en filename"
                            :attachment-id 3201}
                       :fi {:title "fi title"
                            :textcontent "fi filename"
                            :attachment-id 3202}}
       :enabled true
       :archived false}
   33 {:id 33
       :licensetype "attachment"
       :localizations {:en {:title "en title"
                            :textcontent "en filename"
                            :attachment-id 3301}
                       :fi {:title "fi title"
                            :textcontent "fi filename"
                            :attachment-id 3302}}
       :enabled true
       :archived false}
   34 {:id 34
       :licensetype "attachment"
       :localizations {:en {:title "en title"
                            :textcontent "en filename"
                            :attachment-id 3401}
                       :fi {:title "fi title"
                            :textcontent "fi filename"
                            :attachment-id 3402}}
       :enabled true
       :archived false}})

(def ^:private get-user
  {"applicant" {:eppn "applicant"
                :mail "applicant@example.com"
                :commonName "Applicant"}})

(def ^:private get-users-with-role
  {:owner ["owner1"]
   :reporter ["reporter1"]})

(def ^:private get-workflow
  {50 {:id 50
       :organization "org"
       :title "workflow title"
       :workflow {:type "workflow/dynamic"
                  :handlers [{:userid "handler"
                              :name "Handler"
                              :email "handler@example.com"}]}
       :licenses nil
       :owneruserid "owner"
       :modifieruserid "owner"
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}})

;; no attachments here for now
(defn ^:private get-attachments-for-application [id]
  [])

(deftest test-dummies-schema
  (doseq [[description schema dummies] [["form template" schema/FormTemplate get-form-template]
                                        ["catalogue item" schema/CatalogueItem get-catalogue-item]
                                        ["license" schema/License get-license]
                                        ["workflow" schema/Workflow get-workflow]]]
    (doseq [[id dummy] dummies]
      (testing (str description " " id)
        (is (s/validate schema dummy))))))

(deftest test-application-view
  (let [injections {:get-form-template get-form-template
                    :get-catalogue-item get-catalogue-item
                    :get-license get-license
                    :get-user get-user
                    :get-users-with-role get-users-with-role
                    :get-workflow get-workflow
                    :get-attachments-for-application get-attachments-for-application}
        apply-events (fn [events]
                       (let [application (-> events
                                             events/validate-events
                                             (model/build-application-view injections)
                                             permissions/cleanup)]
                         (is (contains? model/states (:application/state application)))
                         application))]

    (testing "created"
      (let [events [{:event/type :application.event/created
                     :event/time (DateTime. 1000)
                     :event/actor "applicant"
                     :application/id 1
                     :application/external-id "extid"
                     :application/resources [{:catalogue-item/id 10
                                              :resource/ext-id "urn:11"}
                                             {:catalogue-item/id 20
                                              :resource/ext-id "urn:21"}]
                     :application/licenses [{:license/id 30}
                                            {:license/id 31}
                                            {:license/id 32}]
                     :form/id 40
                     :workflow/id 50
                     :workflow/type :workflow/dynamic}]
            expected-application {:application/id 1
                                  :application/external-id "extid"
                                  :application/state :application.state/draft
                                  :application/todo nil
                                  :application/created (DateTime. 1000)
                                  :application/modified (DateTime. 1000)
                                  :application/last-activity (DateTime. 1000)
                                  ;; TODO: unify applicant, members, handlers etc. to use either {:userid "user"} or "user"
                                  :application/applicant "applicant"
                                  :application/applicant-attributes {:eppn "applicant"
                                                                     :mail "applicant@example.com"
                                                                     :commonName "Applicant"}
                                  :application/members #{}
                                  :application/past-members #{}
                                  :application/invitation-tokens {}
                                  :application/resources [{:catalogue-item/id 10
                                                           :resource/id 11
                                                           :resource/ext-id "urn:11"
                                                           :catalogue-item/title {:en "en title"
                                                                                  :fi "fi title"}
                                                           :catalogue-item/infourl {:en "http://info.com"}
                                                           :catalogue-item/start (DateTime. 100)
                                                           :catalogue-item/end nil
                                                           :catalogue-item/enabled true
                                                           :catalogue-item/expired false
                                                           :catalogue-item/archived false}
                                                          {:catalogue-item/id 20
                                                           :resource/id 21
                                                           :resource/ext-id "urn:21"
                                                           :catalogue-item/title {:en "en title"
                                                                                  :fi "fi title"}
                                                           :catalogue-item/infourl {:en "http://info.com"}
                                                           :catalogue-item/start (DateTime. 100)
                                                           :catalogue-item/end nil
                                                           :catalogue-item/enabled true
                                                           :catalogue-item/expired false
                                                           :catalogue-item/archived false}]
                                  :application/licenses [{:license/id 30
                                                          :license/type :link
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"}
                                                          :license/link {:en "http://en-license-link"
                                                                         :fi "http://fi-license-link"}
                                                          :license/enabled true
                                                          :license/archived false}
                                                         {:license/id 31
                                                          :license/type :text
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"}
                                                          :license/text {:en "en license text"
                                                                         :fi "fi license text"}
                                                          :license/enabled true
                                                          :license/archived false}
                                                         {:license/id 32
                                                          :license/type :attachment
                                                          :license/title {:en "en title"
                                                                          :fi "fi title"}
                                                          :license/attachment-id {:en 3201
                                                                                  :fi 3202}
                                                          :license/attachment-filename {:en "en filename"
                                                                                        :fi "fi filename"}
                                                          :license/enabled true
                                                          :license/archived false}]
                                  :application/accepted-licenses {}
                                  :application/events events
                                  :application/description ""
                                  :application/form {:form/id 40
                                                     :form/title "form title"
                                                     :form/fields [{:field/id 41
                                                                    :field/value ""
                                                                    :field/type :description
                                                                    :field/title {:en "en title" :fi "fi title"}
                                                                    :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                                                    :field/optional false
                                                                    :field/options []
                                                                    :field/max-length 100}
                                                                   {:field/id 42
                                                                    :field/value ""
                                                                    :field/type :text
                                                                    :field/title {:en "en title" :fi "fi title"}
                                                                    :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                                                    :field/optional false
                                                                    :field/options []
                                                                    :field/max-length 100}]}
                                  :application/attachments []
                                  :application/workflow {:workflow/id 50
                                                         :workflow/type :workflow/dynamic
                                                         :workflow.dynamic/handlers #{"handler"}}}]
        (is (= expected-application (apply-events events)))

        (testing "> draft saved"
          (let [events (conj events {:event/type :application.event/draft-saved
                                     :event/time (DateTime. 2000)
                                     :event/actor "applicant"
                                     :application/id 1
                                     :application/field-values {41 "foo"
                                                                42 "bar"}})
                expected-application (deep-merge expected-application
                                                 {:application/modified (DateTime. 2000)
                                                  :application/last-activity (DateTime. 2000)
                                                  :application/events events
                                                  :application/description "foo"
                                                  :application/accepted-licenses {}
                                                  :application/form {:form/fields [{:field/value "foo"}
                                                                                   {:field/value "bar"}]}})]
            (is (= expected-application (apply-events events)))

            (testing "> copied from"
              (let [events (conj events {:event/type :application.event/copied-from
                                         :event/time (DateTime. 3000)
                                         :event/actor "applicant"
                                         :application/id 1
                                         :application/copied-from {:application/id 42
                                                                   :application/external-id "2019/42"}})
                    expected-application (deep-merge expected-application
                                                     {:application/last-activity (DateTime. 3000)
                                                      :application/events events
                                                      :application/copied-from {:application/id 42
                                                                                :application/external-id "2019/42"}
                                                      :application/form {:form/fields [{:field/previous-value "foo"}
                                                                                       {:field/previous-value "bar"}]}})]
                (is (= expected-application (apply-events events)))))

            (testing "> copied to"
              (let [events (conj events
                                 ;; two copied-to events in order to test the order in which they are shown
                                 {:event/type :application.event/copied-to
                                  :event/time (DateTime. 3000)
                                  :event/actor "applicant"
                                  :application/id 1
                                  :application/copied-to {:application/id 666
                                                          :application/external-id "2020/666"}}
                                 {:event/type :application.event/copied-to
                                  :event/time (DateTime. 3000)
                                  :event/actor "applicant"
                                  :application/id 1
                                  :application/copied-to {:application/id 777
                                                          :application/external-id "2021/777"}})
                    expected-application (deep-merge expected-application
                                                     {:application/last-activity (DateTime. 3000)
                                                      :application/events events
                                                      :application/copied-to [{:application/id 666
                                                                               :application/external-id "2020/666"}
                                                                              {:application/id 777
                                                                               :application/external-id "2021/777"}]})]
                (is (= expected-application (apply-events events)))))

            (testing "> accepted licenses"
              (let [events (conj events {:event/type :application.event/licenses-accepted
                                         :event/time (DateTime. 2500)
                                         :event/actor "applicant"
                                         :application/id 1
                                         :application/accepted-licenses #{30 31 32}})
                    expected-application (deep-merge expected-application
                                                     {:application/last-activity (DateTime. 2500)
                                                      :application/events events
                                                      :application/accepted-licenses {"applicant" #{30 31 32}}})]
                (is (= expected-application (apply-events events)))

                (testing "> resources changed by applicant"
                  (let [events (conj events
                                     {:event/type :application.event/resources-changed
                                      :event/time (DateTime. 2600)
                                      :event/actor "applicant"
                                      :application/id 1
                                      :application/resources [{:catalogue-item/id 10 :resource/ext-id "urn:11"}
                                                              {:catalogue-item/id 20 :resource/ext-id "urn:21"}
                                                              {:catalogue-item/id 30 :resource/ext-id "urn:31"}]
                                      :application/licenses [{:license/id 30}
                                                             {:license/id 31}
                                                             {:license/id 32}
                                                             {:license/id 34}]})
                        expected-application (deep-merge expected-application
                                                         {:application/last-activity (DateTime. 2600)
                                                          :application/modified (DateTime. 2600)
                                                          :application/events events
                                                          :application/resources (conj (:application/resources expected-application)
                                                                                       {:catalogue-item/id 30
                                                                                        :resource/id 31
                                                                                        :resource/ext-id "urn:31"
                                                                                        :catalogue-item/title {:en "en title"
                                                                                                               :fi "fi title"}
                                                                                        :catalogue-item/infourl {:en "http://info.com"}
                                                                                        :catalogue-item/start (DateTime. 100)
                                                                                        :catalogue-item/end nil
                                                                                        :catalogue-item/enabled true
                                                                                        :catalogue-item/expired false
                                                                                        :catalogue-item/archived false})
                                                          :application/licenses (conj (:application/licenses expected-application)
                                                                                      {:license/id 34
                                                                                       :license/type :attachment
                                                                                       :license/title {:en "en title"
                                                                                                       :fi "fi title"}
                                                                                       :license/attachment-id {:en 3401
                                                                                                               :fi 3402}
                                                                                       :license/attachment-filename {:en "en filename"
                                                                                                                     :fi "fi filename"}
                                                                                       :license/enabled true
                                                                                       :license/archived false})})]
                    (is (= expected-application (apply-events events)))))

                (testing "> submitted"
                  (let [events (conj events {:event/type :application.event/submitted
                                             :event/time (DateTime. 3000)
                                             :event/actor "applicant"
                                             :application/id 1})
                        expected-application (merge expected-application
                                                    {:application/last-activity (DateTime. 3000)
                                                     :application/events events
                                                     :application/first-submitted (DateTime. 3000)
                                                     :application/state :application.state/submitted
                                                     :application/todo :new-application})]
                    (is (= expected-application (apply-events events)))

                    (testing "> returned"
                      (let [events (conj events {:event/type :application.event/returned
                                                 :event/time (DateTime. 4000)
                                                 :event/actor "handler"
                                                 :application/id 1
                                                 :application/comment "fix stuff"})
                            expected-application (deep-merge expected-application
                                                             {:application/last-activity (DateTime. 4000)
                                                              :application/events events
                                                              :application/state :application.state/returned
                                                              :application/todo nil
                                                              :application/form {:form/fields [{:field/previous-value "foo"}
                                                                                               {:field/previous-value "bar"}]}})]
                        (is (= expected-application (apply-events events)))

                        (testing "> draft saved x2"
                          (let [events (conj events
                                             {:event/type :application.event/draft-saved
                                              :event/time (DateTime. 5000)
                                              :event/actor "applicant"
                                              :application/id 1
                                              ;; non-submitted versions should not show up as the previous value
                                              :application/field-values {41 "intermediate draft"
                                                                         42 "intermediate draft"}}
                                             {:event/type :application.event/draft-saved
                                              :event/time (DateTime. 6000)
                                              :event/actor "applicant"
                                              :application/id 1
                                              :application/field-values {41 "new foo"
                                                                         42 "new bar"}})
                                expected-application (deep-merge expected-application
                                                                 {:application/modified (DateTime. 6000)
                                                                  :application/last-activity (DateTime. 6000)
                                                                  :application/events events
                                                                  :application/description "new foo"
                                                                  :application/form {:form/fields [{:field/value "new foo"
                                                                                                    :field/previous-value "foo"}
                                                                                                   {:field/value "new bar"
                                                                                                    :field/previous-value "bar"}]}})]
                            (is (= expected-application (apply-events events)))

                            (testing "> submitted"
                              (let [events (conj events
                                                 {:event/type :application.event/submitted
                                                  :event/time (DateTime. 7000)
                                                  :event/actor "applicant"
                                                  :application/id 1})
                                    expected-application (merge expected-application
                                                                {:application/last-activity (DateTime. 7000)
                                                                 :application/events events
                                                                 :application/state :application.state/submitted
                                                                 :application/todo :resubmitted-application})]
                                (is (= expected-application (apply-events events)))))))

                        (testing "> submitted (no draft saved)"
                          (let [events (conj events
                                             {:event/type :application.event/submitted
                                              :event/time (DateTime. 7000)
                                              :event/actor "applicant"
                                              :application/id 1})
                                expected-application (deep-merge expected-application
                                                                 {:application/last-activity (DateTime. 7000)
                                                                  :application/events events
                                                                  :application/state :application.state/submitted
                                                                  :application/todo :resubmitted-application
                                                                  ;; when there was no draft-saved event, the current and
                                                                  ;; previous submitted answers must be the same
                                                                  :application/form {:form/fields [{:field/value "foo"
                                                                                                    :field/previous-value "foo"}
                                                                                                   {:field/value "bar"
                                                                                                    :field/previous-value "bar"}]}})]
                            (is (= expected-application (apply-events events)))))))

                    (testing "> resources changed by handler"
                      (let [events (conj events
                                         {:event/type :application.event/resources-changed
                                          :event/time (DateTime. 3400)
                                          :event/actor "handler"
                                          :application/id 1
                                          :application/comment "You should include this resource."
                                          :application/resources [{:catalogue-item/id 10 :resource/ext-id "urn:11"}
                                                                  {:catalogue-item/id 20 :resource/ext-id "urn:21"}
                                                                  {:catalogue-item/id 30 :resource/ext-id "urn:31"}]
                                          :application/licenses [{:license/id 30}
                                                                 {:license/id 31}
                                                                 {:license/id 32}
                                                                 {:license/id 34}]})
                            expected-application (deep-merge expected-application
                                                             {:application/last-activity (DateTime. 3400)
                                                              :application/modified (DateTime. 3400)
                                                              :application/events events
                                                              :application/resources (conj (:application/resources expected-application)
                                                                                           {:catalogue-item/id 30
                                                                                            :resource/id 31
                                                                                            :resource/ext-id "urn:31"
                                                                                            :catalogue-item/title {:en "en title"
                                                                                                                   :fi "fi title"}
                                                                                            :catalogue-item/infourl {:en "http://info.com"}
                                                                                            :catalogue-item/start (DateTime. 100)
                                                                                            :catalogue-item/end nil
                                                                                            :catalogue-item/enabled true
                                                                                            :catalogue-item/expired false
                                                                                            :catalogue-item/archived false})
                                                              :application/licenses (conj (:application/licenses expected-application)
                                                                                          {:license/id 34
                                                                                           :license/type :attachment
                                                                                           :license/title {:en "en title"
                                                                                                           :fi "fi title"}
                                                                                           :license/attachment-id {:en 3401
                                                                                                                   :fi 3402}
                                                                                           :license/attachment-filename {:en "en filename"
                                                                                                                         :fi "fi filename"}
                                                                                           :license/enabled true
                                                                                           :license/archived false})})]
                        (is (= expected-application (apply-events events)))))
                    (testing "> licenses added"
                      (let [events (conj events
                                         {:event/type :application.event/licenses-added
                                          :event/time (DateTime. 3500)
                                          :event/actor "handler"
                                          :application/id 1
                                          :application/licenses [{:license/id 33}]
                                          :application/comment "Please sign these terms also"})
                            expected-application (merge expected-application
                                                        {:application/last-activity (DateTime. 3500)
                                                         :application/modified (DateTime. 3500)
                                                         :application/events events
                                                         :application/licenses (conj (:application/licenses expected-application)
                                                                                     {:license/id 33
                                                                                      :license/type :attachment
                                                                                      :license/title {:en "en title"
                                                                                                      :fi "fi title"}
                                                                                      :license/attachment-id {:en 3301
                                                                                                              :fi 3302}
                                                                                      :license/attachment-filename {:en "en filename"
                                                                                                                    :fi "fi filename"}
                                                                                      :license/enabled true
                                                                                      :license/archived false})})]
                        (is (= expected-application (apply-events events)))

                        (testing "> approved"
                          (let [events (conj events
                                             {:event/type :application.event/approved
                                              :event/time (DateTime. 4000)
                                              :event/actor "handler"
                                              :application/id 1
                                              :application/comment "looks good"})
                                expected-application (merge expected-application
                                                            {:application/last-activity (DateTime. 4000)
                                                             :application/events events
                                                             :application/state :application.state/approved
                                                             :application/todo nil})]
                            (is (= expected-application (apply-events events)))

                            (testing "> resources changed by handler"
                              (let [events (conj events
                                                 {:event/type :application.event/resources-changed
                                                  :event/time (DateTime. 4500)
                                                  :event/actor "handler"
                                                  :application/id 1
                                                  :application/comment "I changed the resources"
                                                  :application/resources [{:catalogue-item/id 10 :resource/ext-id "urn:11"}
                                                                          {:catalogue-item/id 30 :resource/ext-id "urn:31"}]
                                                  :application/licenses [{:license/id 30}
                                                                         {:license/id 31}
                                                                         {:license/id 32}
                                                                         ;; Include also the previously added license #33 in the new licenses.
                                                                         {:license/id 33}
                                                                         {:license/id 34}]})
                                    expected-application (deep-merge expected-application
                                                                     {:application/last-activity (DateTime. 4500)
                                                                      :application/modified (DateTime. 4500)
                                                                      :application/events events
                                                                      :application/resources [{:catalogue-item/id 10
                                                                                               :resource/id 11
                                                                                               :resource/ext-id "urn:11"
                                                                                               :catalogue-item/title {:en "en title"
                                                                                                                      :fi "fi title"}
                                                                                               :catalogue-item/infourl {}
                                                                                               :catalogue-item/start (DateTime. 100)
                                                                                               :catalogue-item/end nil
                                                                                               :catalogue-item/enabled true
                                                                                               :catalogue-item/expired false
                                                                                               :catalogue-item/archived false}
                                                                                              {:catalogue-item/id 30
                                                                                               :resource/id 31
                                                                                               :resource/ext-id "urn:31"
                                                                                               :catalogue-item/title {:en "en title"
                                                                                                                      :fi "fi title"}
                                                                                               :catalogue-item/infourl {}
                                                                                               :catalogue-item/start (DateTime. 100)
                                                                                               :catalogue-item/end nil
                                                                                               :catalogue-item/enabled true
                                                                                               :catalogue-item/expired false
                                                                                               :catalogue-item/archived false}]
                                                                      :application/licenses (conj (:application/licenses expected-application)
                                                                                                  {:license/id 34
                                                                                                   :license/type :attachment
                                                                                                   :license/title {:en "en title"
                                                                                                                   :fi "fi title"}
                                                                                                   :license/attachment-id {:en 3401
                                                                                                                           :fi 3402}
                                                                                                   :license/attachment-filename {:en "en filename"
                                                                                                                                 :fi "fi filename"}
                                                                                                   :license/enabled true
                                                                                                   :license/archived false})})]
                                (is (= expected-application (apply-events events)))))

                            (testing "> licenses accepted"
                              (let [events (conj events
                                                 {:event/type :application.event/licenses-accepted
                                                  :event/time (DateTime. 4500)
                                                  :event/actor "applicant"
                                                  :application/id 1
                                                  :application/accepted-licenses #{30 31 32 33}})
                                    expected-application (merge expected-application
                                                                {:application/last-activity (DateTime. 4500)
                                                                 :application/events events
                                                                 :application/accepted-licenses {"applicant" #{30 31 32 33}}})]
                                (is (= expected-application (apply-events events)))

                                (testing "> member added"
                                  (let [events (conj events
                                                     {:event/type :application.event/member-added
                                                      :event/time (DateTime. 4600)
                                                      :event/actor "handler"
                                                      :application/id 1
                                                      :application/member {:userid "member"}})
                                        expected-application (merge expected-application
                                                                    {:application/last-activity (DateTime. 4600)
                                                                     :application/events events
                                                                     :application/members #{{:userid "member"}}})]
                                    (is (= expected-application (apply-events events)))
                                    (testing "> licenses accepted for new member"
                                      (let [events (conj events
                                                         {:event/type :application.event/licenses-accepted
                                                          :event/time (DateTime. 4700)
                                                          :event/actor "member"
                                                          :application/id 1
                                                          :application/accepted-licenses #{30 33}})
                                            expected-application (merge expected-application
                                                                        {:application/last-activity (DateTime. 4700)
                                                                         :application/events events
                                                                         :application/accepted-licenses {"applicant" #{30 31 32 33}
                                                                                                         "member" #{30 33}}})]
                                        (is (= expected-application (apply-events events)))
                                        (testing "> licenses accepted overwrites previous"
                                          (let [events (conj events
                                                             {:event/type :application.event/licenses-accepted
                                                              :event/time (DateTime. 4800)
                                                              :event/actor "member"
                                                              :application/id 1
                                                              :application/accepted-licenses #{31 32}})
                                                expected-application (merge expected-application
                                                                            {:application/last-activity (DateTime. 4800)
                                                                             :application/events events
                                                                             :application/accepted-licenses {"applicant" #{30 31 32 33}
                                                                                                             "member" #{31 32}}})]
                                            (is (= expected-application (apply-events events)))))))))


                                (testing "> closed"
                                  (let [events (conj events
                                                     {:event/type :application.event/closed
                                                      :event/time (DateTime. 5000)
                                                      :event/actor "handler"
                                                      :application/id 1
                                                      :application/comment "the project is finished"})
                                        expected-application (merge expected-application
                                                                    {:application/last-activity (DateTime. 5000)
                                                                     :application/events events
                                                                     :application/state :application.state/closed
                                                                     :application/todo nil})]
                                    (is (= expected-application (apply-events events)))))))))

                        (testing "> rejected"
                          (let [events (conj events
                                             {:event/type :application.event/rejected
                                              :event/time (DateTime. 4000)
                                              :event/actor "handler"
                                              :application/id 1
                                              :application/comment "never gonna happen"})
                                expected-application (merge expected-application
                                                            {:application/last-activity (DateTime. 4000)
                                                             :application/events events
                                                             :application/state :application.state/rejected
                                                             :application/todo nil})]
                            (is (= expected-application (apply-events events)))))

                        (testing "> comment requested"
                          (let [request-id (UUID/fromString "4de6c2b0-bb2e-4745-8f92-bd1d1f1e8298")
                                events (conj events
                                             {:event/type :application.event/comment-requested
                                              :event/time (DateTime. 4000)
                                              :event/actor "handler"
                                              :application/id 1
                                              :application/request-id request-id
                                              :application/commenters ["commenter"]
                                              :application/comment "please comment"})
                                expected-application (deep-merge expected-application
                                                                 {:application/last-activity (DateTime. 4000)
                                                                  :application/events events
                                                                  :application/todo :waiting-for-review
                                                                  :rems.application.model/latest-comment-request-by-user {"commenter" request-id}})]
                            (is (= expected-application (apply-events events)))

                            (testing "> commented"
                              (let [events (conj events
                                                 {:event/type :application.event/commented
                                                  :event/time (DateTime. 5000)
                                                  :event/actor "commenter"
                                                  :application/id 1
                                                  :application/request-id request-id
                                                  :application/comment "looks good"})
                                    expected-application (merge expected-application
                                                                {:application/last-activity (DateTime. 5000)
                                                                 :application/events events
                                                                 :application/todo :no-pending-requests
                                                                 :rems.application.model/latest-comment-request-by-user {}})]
                                (is (= expected-application (apply-events events)))))))

                        (testing "> decision requested"
                          (let [request-id (UUID/fromString "db9c7fd6-53be-4b04-b15d-a3a8e0a45e49")
                                events (conj events
                                             {:event/type :application.event/decision-requested
                                              :event/time (DateTime. 4000)
                                              :event/actor "handler"
                                              :application/id 1
                                              :application/request-id request-id
                                              :application/deciders ["decider"]
                                              :application/comment "please decide"})
                                expected-application (merge expected-application
                                                            {:application/last-activity (DateTime. 4000)
                                                             :application/events events
                                                             :application/todo :waiting-for-decision
                                                             :rems.application.model/latest-decision-request-by-user {"decider" request-id}})]
                            (is (= expected-application (apply-events events)))

                            (testing "> decided"
                              (let [events (conj events
                                                 {:event/type :application.event/decided
                                                  :event/time (DateTime. 5000)
                                                  :event/actor "decider"
                                                  :application/id 1
                                                  :application/request-id request-id
                                                  :application/decision :approved
                                                  :application/comment "I approve this"})
                                    expected-application (merge expected-application
                                                                {:application/last-activity (DateTime. 5000)
                                                                 :application/events events
                                                                 :application/todo :no-pending-requests
                                                                 :rems.application.model/latest-decision-request-by-user {}})]
                                (is (= expected-application (apply-events events)))))))

                        (testing "> member invited"
                          (let [token "b187bda7b9da9053a5d8b815b029e4ba"
                                events (conj events
                                             {:event/type :application.event/member-invited
                                              :event/time (DateTime. 4000)
                                              :event/actor "applicant"
                                              :application/id 1
                                              :application/member {:name "Mr. Member"
                                                                   :email "member@example.com"}
                                              :invitation/token token})
                                expected-application (deep-merge expected-application
                                                                 {:application/last-activity (DateTime. 4000)
                                                                  :application/events events
                                                                  :application/invitation-tokens {token {:name "Mr. Member"
                                                                                                         :email "member@example.com"}}})]
                            (is (= expected-application (apply-events events)))

                            (testing "> member uninvited"
                              (let [events (conj events
                                                 {:event/type :application.event/member-uninvited
                                                  :event/time (DateTime. 5000)
                                                  :event/actor "applicant"
                                                  :application/id 1
                                                  :application/member {:name "Mr. Member"
                                                                       :email "member@example.com"}
                                                  :application/comment "he left the project"})
                                    expected-application (merge expected-application
                                                                {:application/last-activity (DateTime. 5000)
                                                                 :application/events events
                                                                 :application/invitation-tokens {}})]
                                (is (= expected-application (apply-events events)))))

                            (testing "> member joined"
                              (let [events (conj events
                                                 {:event/type :application.event/member-joined
                                                  :event/time (DateTime. 5000)
                                                  :event/actor "member"
                                                  :application/id 1
                                                  :invitation/token token})
                                    expected-application (merge expected-application
                                                                {:application/last-activity (DateTime. 5000)
                                                                 :application/events events
                                                                 :application/members #{{:userid "member"}}
                                                                 :application/invitation-tokens {}})]
                                (is (= expected-application (apply-events events)))))))

                        (testing "> member added"
                          (let [events (conj events
                                             {:event/type :application.event/member-added
                                              :event/time (DateTime. 4000)
                                              :event/actor "handler"
                                              :application/id 1
                                              :application/member {:userid "member"}})
                                expected-application (merge expected-application
                                                            {:application/last-activity (DateTime. 4000)
                                                             :application/events events
                                                             :application/members #{{:userid "member"}}})]
                            (is (= expected-application (apply-events events)))

                            (testing "> member removed"
                              (let [events (conj events
                                                 {:event/type :application.event/member-removed
                                                  :event/time (DateTime. 5000)
                                                  :event/actor "applicant"
                                                  :application/id 1
                                                  :application/member {:userid "member"}
                                                  :application/comment "he left the project"})
                                    expected-application (merge expected-application
                                                                {:application/last-activity (DateTime. 5000)
                                                                 :application/events events
                                                                 :application/members #{}
                                                                 :application/past-members #{{:userid "member"}}})]
                                (is (= expected-application (apply-events events)))))))))))))))))))

(deftest test-calculate-permissions
  ;; TODO: is this what we want? wouldn't it be useful to be able to write more than one comment?
  (testing "commenter may comment only once"
    (let [requested (reduce model/calculate-permissions nil [{:event/type :application.event/created
                                                              :event/actor "applicant"}
                                                             {:event/type :application.event/submitted
                                                              :event/actor "applicant"}
                                                             {:event/type :application.event/comment-requested
                                                              :event/actor "handler"
                                                              :application/commenters ["commenter1" "commenter2"]}])
          commented (reduce model/calculate-permissions requested [{:event/type :application.event/commented
                                                                    :event/actor "commenter1"}])]
      (is (= #{:see-everything :application.command/comment :application.command/remark}
             (permissions/user-permissions requested "commenter1")))
      (is (= #{:see-everything :application.command/remark}
             (permissions/user-permissions commented "commenter1")))
      (is (= #{:see-everything :application.command/comment :application.command/remark}
             (permissions/user-permissions commented "commenter2")))))

  (testing "decider may decide only once"
    (let [requested (reduce model/calculate-permissions nil [{:event/type :application.event/created
                                                              :event/actor "applicant"}
                                                             {:event/type :application.event/submitted
                                                              :event/actor "applicant"}
                                                             {:event/type :application.event/decision-requested
                                                              :event/actor "handler"
                                                              :application/deciders ["decider"]}])
          decided (reduce model/calculate-permissions requested [{:event/type :application.event/decided
                                                                  :event/actor "decider"}])]
      (is (= #{:see-everything :application.command/decide :application.command/remark}
             (permissions/user-permissions requested "decider")))
      (is (= #{:see-everything :application.command/remark}
             (permissions/user-permissions decided "decider")))))

  (testing "everyone can accept invitation"
    (let [created (reduce model/calculate-permissions nil [{:event/type :application.event/created
                                                            :event/actor "applicant"}])]
      (is (contains? (permissions/user-permissions created "joe")
                     :application.command/accept-invitation))))
  (testing "nobody can accept invitation for closed application"
    (let [closed (reduce model/calculate-permissions nil [{:event/type :application.event/created
                                                           :event/actor "applicant"}
                                                          {:event/type :application.event/closed
                                                           :event/actor "applicant"}])]
      (is (not (contains? (permissions/user-permissions closed "joe")
                          :application.command/accept-invitation)))
      (is (not (contains? (permissions/user-permissions closed "applicant")
                          :application.command/accept-invitation))))))

(deftest test-apply-user-permissions
  (let [application (-> (model/application-view nil {:event/type :application.event/created
                                                     :event/actor "applicant"})
                        (assoc-in [:application/workflow :workflow.dynamic/handlers] #{"handler"})
                        (permissions/give-role-to-users :handler ["handler"])
                        (permissions/give-role-to-users :role-1 ["user-1"])
                        (permissions/give-role-to-users :role-2 ["user-2"])
                        (permissions/update-role-permissions {:role-1 #{}
                                                              :role-2 #{:foo :bar}}))]
    (testing "users with a role can see the application"
      (is (not (nil? (model/apply-user-permissions application "user-1")))))
    (testing "users without a role cannot see the application"
      (is (nil? (model/apply-user-permissions application "user-3"))))
    (testing "lists the user's permissions"
      (is (= #{} (:application/permissions (model/apply-user-permissions application "user-1"))))
      (is (= #{:foo :bar} (:application/permissions (model/apply-user-permissions application "user-2")))))
    (testing "lists the user's roles"
      (is (= #{:role-1} (:application/roles (model/apply-user-permissions application "user-1"))))
      (is (= #{:role-2} (:application/roles (model/apply-user-permissions application "user-2")))))

    (let [all-events [{:event/type :application.event/created}
                      {:event/type :application.event/submitted}
                      {:event/type :application.event/comment-requested}
                      {:event/type :application.event/remarked
                       :application/public true}
                      {:event/type :application.event/remarked
                       :application/public false}]
          restricted-events [{:event/type :application.event/created}
                             {:event/type :application.event/submitted}
                             {:event/type :application.event/remarked
                              :application/public true}]
          application (-> application
                          (assoc :application/events all-events)
                          (permissions/update-role-permissions {:role-1 #{:see-everything}}))]
      (testing "privileged users"
        (let [application (model/apply-user-permissions application "user-1")]
          (testing "see all events"
            (is (= all-events
                   (:application/events application))))
          (testing "see dynamic workflow handlers"
            (is (= #{"handler"}
                   (get-in application [:application/workflow :workflow.dynamic/handlers]))))))

      (testing "normal users"
        (let [application (model/apply-user-permissions application "user-2")]
          (testing "see only some events"
            (is (= restricted-events
                   (:application/events application))))
          (testing "don't see dynamic workflow handlers"
            (is (= nil
                   (get-in application [:application/workflow :workflow.dynamic/handlers])))))))

    (testing "invitation tokens are not visible to anybody"
      (let [application (model/application-view application {:event/type :application.event/member-invited
                                                             :application/member {:name "member"
                                                                                  :email "member@example.com"}
                                                             :invitation/token "secret"})]
        (testing "- original"
          (is (= #{"secret" nil} (set (map :invitation/token (:application/events application)))))
          (is (= {"secret" {:name "member"
                            :email "member@example.com"}}
                 (:application/invitation-tokens application)))
          (is (= nil
                 (:application/invited-members application))))
        (doseq [user-id ["applicant" "handler"]]
          (testing (str "- as user " user-id)
            (let [application (model/apply-user-permissions application user-id)]
              (is (= #{nil} (set (map :invitation/token (:application/events application)))))
              (is (= nil
                     (:application/invitation-tokens application)))
              (is (= #{{:name "member"
                        :email "member@example.com"}}
                     (:application/invited-members application))))))))

    (testing "personalized waiting for your review"
      (let [application (model/application-view application {:event/type :application.event/comment-requested
                                                             :event/actor "handler"
                                                             :application/commenters ["reviewer1"]})]
        (is (= :waiting-for-review
               (:application/todo (model/apply-user-permissions application "handler")))
            "as seen by handler")
        (is (= :waiting-for-your-review
               (:application/todo (model/apply-user-permissions application "reviewer1")))
            "as seen by reviewer")))

    (testing "personalized waiting for your decision"
      (let [application (model/application-view application {:event/type :application.event/decision-requested
                                                             :event/actor "handler"
                                                             :application/deciders ["decider1"]})]
        (is (= :waiting-for-decision
               (:application/todo (model/apply-user-permissions application "handler")))
            "as seen by handler")
        (is (= :waiting-for-your-decision
               (:application/todo (model/apply-user-permissions application "decider1")))
            "as seen by decider")))))
