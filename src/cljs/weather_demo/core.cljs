(ns weather-demo.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]

              [reagent-forms.core :refer [bind-fields]]
              [ajax.core :refer [GET json-response-format]]
              [clojure.string :as string]))

(def app-state (atom {}))
(def cities (reagent/cursor app-state [:cities]))
(def city-id (reagent/cursor app-state [:city :id]))

;; -------------------------
;; Components

(defn hc-chart-draw [this]
  (let [node (reagent/dom-node this)
        {:keys [config]} (reagent/props this)]
    (when config
      (js/Highcharts.Chart.
        (clj->js (assoc-in config [:chart :renderTo] node))))))

(defn hc-chart [config]
  (reagent/create-class {:display-name "highcharts"
                         :reagent-render (fn [] [:div])
                         :component-did-mount hc-chart-draw
                         :component-did-update hc-chart-draw}))

;; -------------------------
;; Views

(defn country-name [country]
  (let [name (-> country :name :common)
        region (-> country :region)]
    (string/join ", " (remove empty? [name region]))))

(defn country-selector [countries]
  [:select {:field :list :id :id}
   (for [country (sort-by country-name countries)]
     [:option {:key (:cca2 country)} (country-name country)])])

(defn country-field [countries country-atom]
  (println "Rendering country field for" (count countries) "countries")
  (if-not countries [:select [:option "Loading countries..."]]
    [bind-fields (country-selector countries) country-atom]))

(defn country-description [countries country-id]
  (println "Rendering country description for" country-id)
  (if-not country-id [:p "No country selected"]
    (let [country (first (filter #(= (:cca2 %) country-id) countries))]
      [:p "Selected country is " (country-name country)])))

(defn city-selector [cities]
  [:select {:field :list :id :city.id}
   (for [city (sort-by :name cities)]
     [:option {:key (:_id city)} (:name city)])])

(defn city-field []
  (println "Rendering city field for" (count @cities) "cities")
  (if-not @cities [:select [:option "Loading cities..."]]
    [(bind-fields (city-selector @cities) app-state)]))

(defn load-city-field [country-id]
  (when country-id
    (reset! cities nil)
    (reset! city-id nil)
    (println "Loading cities for" country-id)
    (GET (str "/api/cities/" country-id)
      {:handler #(reset! cities %)
       :response-format (json-response-format {:keywords? true})}))
  [city-field])

(defn dt->date [dt]
  (js/Date. (* dt 1000)))

;; function timeFormatter() {
;;   var input = this;
;;   return input['value'].toLocaleTimeString(navigator.language, {
;;     hour: '2-digit',
;;     minute: '2-digit'
;;   });
;; }
(defn time-formatter []
  (this-as input
    (.toLocaleTimeString
      (aget input "value")
      js/navigator.language
      (clj->js {:hour "2-digit" :minute "2-digit"}))))

(defn forecast-config [forecast]
  (let [city-name (-> forecast :city :name)
        entries (take 10 (-> forecast :list))
        time (map (comp dt->date :dt) entries)
        rain (map (comp :3h :rain) entries)
        snow (map (comp :3h :snow) entries)
        prcp (map (fnil + 0 0) rain snow)
        temp (map (comp :temp :main) entries)]
    {:chart {:type "column"
             :marginLeft 20}
     :title {:text (str "Weather forecast for " city-name)}
     :xAxis {:type "datetime"
             :categories time
             :labels {:formatter time-formatter}}
     :yAxis [
       {:labels {:format "{value}°C"
                 :style {:color "blue"}}
        :title {:text nil}
        :opposite true}
       {:labels {:format "{value}mm"
                 :style {:color "#4572A7"}}
        :title {:text nil}
        :opposite true}
     ]
     :tooltip {:shared true}
     :series [
       {:type "column" :name "Precipitation" :color "#A0A0A0" :yAxis 1 :data prcp}
       {:type "spline" :name "Temperature" :color "blue" :yAxis 0 :data temp}
     ]}))

(defn forecast-chart [forecast-atom]
  (println "Rendering forecast chart for" (-> @forecast-atom :city :name))
  (if-not @forecast-atom [:div "Wait while the chart is loading..."]
    [hc-chart {:config (forecast-config @forecast-atom)}]))

(defn load-forecast-chart [city-id]
  (let [forecast (atom nil)]
    (fn [city-id]
      (when city-id
        (reset! forecast nil)
        (println "Loading forecast data for city" city-id)
        (GET "data/2.5/forecast"
          {:params {:units "metric"
                    :id city-id}
           :handler #(reset! forecast %)
           :response-format (json-response-format {:keywords? true})}))
      [forecast-chart forecast])))

(defn home-page []
  [:div [:h2 "Welcome to weather-demo"]
   [:div "You might want to check out the " [:a {:href "/weather"} "weather"]]])

(defn weather-page []
  (let [countries (atom nil)
        selected-country (atom nil)]
    (GET "/api/countries/"
      {:handler #(reset! countries %)
       :response-format (json-response-format {:keywords? true})})
    (fn []
      [:div [:h2 "Weather page"]
       [country-field @countries selected-country]
       [load-city-field (:id @selected-country)]
       [country-description @countries (:id @selected-country)]
       [load-forecast-chart @city-id]])))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/weather" []
  (session/put! :current-page #'weather-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))
