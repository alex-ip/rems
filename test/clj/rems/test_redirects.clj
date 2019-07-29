(ns ^:integration rems.test-redirects
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.core :as db]
            [rems.db.test-data :as test-data]
            [rems.handler :refer [handler]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(defn disable-catalogue-item [catid]
  (db/set-catalogue-item-state! {:id catid :enabled false}))

(deftest redirect-to-new-application-test
  (testing "redirects to new application page for catalogue item matching the resource ID"
    (let [resid (test-data/create-resource! {:resource-ext-id "urn:one-matching-resource"})
          catid (test-data/create-catalogue-item! {:resource-id resid})
          response (-> (request :get "/apply-for?resource=urn:one-matching-resource")
                       handler)]
      (is (= 302 (:status response)))
      (is (= (str "http://localhost/#/application?items=" catid) (get-in response [:headers "Location"])))))

  (testing "fails if no catalogue item is found"
    (let [response (-> (request :get "/apply-for?resource=urn:no-such-resource")
                       handler)]
      (is (= 404 (:status response)))
      (is (= "Resource not found" (read-body response)))))

  (testing "fails if more than one catalogue item is found"
    (let [resid (test-data/create-resource! {:resource-ext-id "urn:two-matching-resources"})
          _ (test-data/create-catalogue-item! {:resource-id resid})
          _ (test-data/create-catalogue-item! {:resource-id resid})
          response (-> (request :get "/apply-for?resource=urn:two-matching-resources")
                       handler)]
      (is (= 404 (:status response)))
      (is (= "Resource ID is not unique" (read-body response)))))

  (testing "redirects to active catalogue item, ignoring disabled items for the same resource ID"
    (let [resid (test-data/create-resource! {:resource-ext-id "urn:enabled-and-disabled-items"})
          old-catid (test-data/create-catalogue-item! {:resource-id resid})
          _ (disable-catalogue-item old-catid)
          new-catid (test-data/create-catalogue-item! {:resource-id resid})
          response (-> (request :get "/apply-for?resource=urn:enabled-and-disabled-items")
                       handler)]
      (is (= 302 (:status response)))
      (is (= (str "http://localhost/#/application?items=" new-catid) (get-in response [:headers "Location"]))))))
