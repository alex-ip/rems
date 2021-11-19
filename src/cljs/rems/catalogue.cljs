(ns rems.catalogue
  (:require [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.common.application-util :refer [form-fields-editable?]]
            [rems.atoms :refer [external-link document-title document-title]]
            [rems.cart :as cart]
            [rems.common.catalogue-util :refer [catalogue-item-more-info-url]]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.common.roles :as roles]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.tree :as tree]
            [rems.text :refer [text get-localized-title]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db ::catalogue ::draft-applications)
    :dispatch-n [[::full-catalogue]
                 (when (roles/is-logged-in? (get-in db [:identity :roles])) [::draft-applications])
                 [:rems.table/reset]]}))

(fetcher/reg-fetcher ::full-catalogue "/api/catalogue")

(rf/reg-sub
 ::catalogue
 (fn [_ _]
   (rf/subscribe [::full-catalogue]))
 (fn [catalogue _]
   (->> catalogue
        (filter :enabled)
        (remove :expired))))

(defn- filter-drafts-only [applications]
  (filter form-fields-editable? applications))

(fetcher/reg-fetcher ::draft-applications "/api/my-applications" {:result filter-drafts-only})

;;;; UI

(defn- catalogue-item-more-info [item language config]
  (let [link (catalogue-item-more-info-url item language config)]
    (when link
      [:a.btn.btn-secondary
       {:href link
        :target :_blank
        :aria-label (str (text :t.catalogue/more-info)
                         ": "
                         (get-localized-title item language)
                         ", "
                         (text :t.link/opens-in-new-window))}
       [external-link] " " (text :t.catalogue/more-info)])))

(rf/reg-sub
 ::catalogue-table-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:language])
    (rf/subscribe [:logged-in])
    (rf/subscribe [:rems.cart/cart])
    (rf/subscribe [:rems.config/config])])
 (fn [[catalogue language logged-in? cart config] _]
   (let [cart-item-ids (set (map :id cart))]
     (map (fn [item]
            {:key (:id item)
             :name {:value (get-localized-title item language)}
             :commands {:td [:td.commands
                             [catalogue-item-more-info item language config]
                             (when logged-in?
                               (if (contains? cart-item-ids (:id item))
                                 [cart/remove-from-cart-button item language]
                                 [cart/add-to-cart-button item language]))]}})
          catalogue))))

(rf/reg-sub
 ::catalogue-tree-rows
 (fn [_ _]
   [(rf/subscribe [::catalogue])
    (rf/subscribe [:language])
    (rf/subscribe [:logged-in])
    (rf/subscribe [:rems.cart/cart])
    (rf/subscribe [:rems.config/config])])
 (fn [[catalogue language logged-in? cart config] _]
   (let [cart-item-ids (set (map :id cart))]
     (map (fn [item]
            {:key (:id item)
             :name {:value (get-localized-title item language)}
             :commands {:td [:td.commands
                             [catalogue-item-more-info item language config]
                             (when logged-in?
                               (if (contains? cart-item-ids (:id item))
                                 [cart/remove-from-cart-button item language]
                                 [cart/add-to-cart-button item language]))]}})
          catalogue))))

(defn draft-application-list []
  (let [applications ::draft-applications]
    (when (seq @(rf/subscribe [applications]))
      [:div
       [:h2 (text :t.catalogue/continue-existing-application)]
       (text :t.catalogue/continue-existing-application-intro)
       [application-list/list
        {:id applications
         :applications applications
         :visible-columns #{:resource :last-activity :view}
         :default-sort-column :last-activity
         :default-sort-order :desc}]])))

(defn- catalogue-tree []
  (let [catalogue {:id ::catalogue
                   :columns [{:key :name
                              :title (text :t.catalogue/header)}
                             {:key :commands
                              :sortable? false
                              :filterable? false}]
                   :rows [::catalogue-tree-rows]
                   :default-sort-column :name}]
    [:div
     #_[table/search catalogue]
     [tree/tree catalogue]]))

(defn catalogue-page []
  [:div
   [document-title (text :t.catalogue/catalogue)]
   [flash-message/component :top]
   (text :t.catalogue/intro)
   (if (or @(rf/subscribe [::full-catalogue :fetching?])
           @(rf/subscribe [::draft-applications :fetching?]))
     [spinner/big]
     [:div
      (when @(rf/subscribe [:logged-in])
        [:<>
         [draft-application-list]
         [cart/cart-list-container]
         [:h2 (text :t.catalogue/apply-resources)]])
      [catalogue-tree]])])
