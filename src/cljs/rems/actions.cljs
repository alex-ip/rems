(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.guide-functions]
            [rems.spinner :as spinner]
            [rems.text :refer [text]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::actions ::handled-actions) ; zero state that should be reloaded, good for performance
    :dispatch [::fetch-actions]}))

;;;; actions

(rf/reg-event-fx
 ::fetch-actions
 (fn [{:keys [db]} _]
   (fetch "/api/applications/todo"
          {:handler #(rf/dispatch [::fetch-actions-result %])})
   {:db (assoc db ::loading-actions? true)}))

(rf/reg-event-db
 ::fetch-actions-result
 (fn [db [_ result]]
   (-> db
       (assoc ::actions result)
       (dissoc ::loading-actions?))))

(rf/reg-sub
 ::actions
 (fn [db _]
   (::actions db)))

(rf/reg-sub
 ::loading-actions?
 (fn [db _]
   (::loading-actions? db)))

;;;; handled actions

(rf/reg-event-fx
 ::fetch-handled-actions
 (fn [{:keys [db]} _]
   (fetch "/api/applications/handled"
          {:handler #(rf/dispatch [::fetch-handled-actions-result %])})
   {:db (assoc db ::loading-handled-actions? true)}))

(rf/reg-event-db
 ::fetch-handled-actions-result
 (fn [db [_ result]]
   (-> db
       (assoc ::handled-actions result)
       (dissoc ::loading-handled-actions?))))

(rf/reg-sub
 ::handled-actions
 (fn [db _]
   (::handled-actions db)))

(rf/reg-sub
 ::loading-handled-actions?
 (fn [db _]
   (::loading-handled-actions? db)))

;;;; table sorting

;; Because we want to display multiple independently sortable
;; application tables, we store a map of sort types in the db.

(rf/reg-sub
 ::sorting
 (fn [db [_ key]]
   (get-in db [::sorting key]
           {:sort-column :last-activity
            :sort-order :desc})))

(rf/reg-event-db ::set-sorting (fn [db [_ key sorting]] (assoc-in db [::sorting key] sorting)))

(rf/reg-sub ::filtering (fn [db [_ key]] (get-in db [::filtering key])))
(rf/reg-event-db ::set-filtering (fn [db [_ key filtering]] (assoc-in db [::filtering key] filtering)))

;;;; UI

;; TODO not implemented
(defn- load-application-states-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#load-application-states-modal" :disabled true}
   (text :t.actions/load-application-states)])

(defn- export-entitlements-button []
  [:a.btn.btn-secondary
   {:href "/entitlements.csv"}
   (text :t.actions/export-entitlements)])

;; TODO not implemented
(defn- show-publications-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#show-publications-modal" :disabled true}
   (text :t.actions/show-publications)])

;; TODO not implemented
(defn- show-throughput-times-button []
  [:button.btn.btn-secondary {:type :button :data-toggle "modal" :data-target "#show-throughput-times-modal" :disabled true}
   (text :t.actions/show-throughput-times)])

(defn- report-buttons []
  [:div.form-actions.inline
   [load-application-states-button]
   [export-entitlements-button]
   [show-publications-button]
   [show-throughput-times-button]])

(defn- open-applications []
  (let [apps @(rf/subscribe [::actions])
        config @(rf/subscribe [:rems.config/config])
        id-column (get config :application-id-column :id)]
    (if (empty? apps)
      [:div.actions.alert.alert-success (text :t.actions/empty)]
      [application-list/component2
       {:id ::open-applications
        :applications ::actions
        :visible-columns #{id-column :description :resource :applicant :state :submitted :last-activity :view}
        :default-sort-column :last-activity
        :default-sort-order :desc}])))

(defn- handled-applications
  "Creates a table containing a list of handled applications.

  The function takes the following parameters as arguments:
  key:         key to use for table ordering in re-frame
  apps:        collection of apps to be shown
  top-buttons: a set of extra buttons that will be shown on top of the table. This could include f.ex 'export as pdf' button."
  [apps top-buttons loading?]
  (if loading?
    [spinner/big]
    (if (empty? apps)
      [:div.actions.alert.alert-success (text :t.actions/no-handled-yet)]
      [:div
       top-buttons
       [application-list/component
        {:visible-columns (into [(get @(rf/subscribe [:rems.config/config]) :application-id-column :id)]
                                [:description :resource :applicant :state :last-activity :view])
         :sorting (assoc @(rf/subscribe [::sorting ::handled-applications])
                         :set-sorting #(rf/dispatch [::set-sorting ::handled-applications %]))
         :filtering (assoc @(rf/subscribe [::filtering ::handled-applications])
                           :set-filtering #(rf/dispatch [::set-filtering ::handled-applications %]))
         :items apps}]])))

(defn actions-page []
  (let [handled-actions @(rf/subscribe [::handled-actions])]
    [:div
     [document-title (text :t.navigation/actions)]
     (if @(rf/subscribe [::loading-actions?])
       [spinner/big]
       [:div.spaced-sections
        [collapsible/component
         {:id "open-approvals"
          :open? true
          :title (text :t.actions/open-approvals)
          :collapse [open-applications]}]
        [collapsible/component
         {:id "handled-approvals"
          :on-open #(rf/dispatch [::fetch-handled-actions])
          :title (text :t.actions/handled-approvals)
          :collapse [handled-applications handled-actions nil @(rf/subscribe [::loading-handled-actions?])]}]])]))
