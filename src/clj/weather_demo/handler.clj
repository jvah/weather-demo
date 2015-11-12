(ns weather-demo.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env]]

            [clojure.java.io :as io]
            [cheshire.core :as json]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [clj-http.client :as http])
  (:import [java.util.zip GZIPInputStream]))

(def countries
  (with-open [rdr (io/reader "https://raw.githubusercontent.com/mledoze/countries/master/countries.json")]
    (doall (json/parse-stream rdr))))

(def cities
  (with-open [input (io/input-stream "http://bulk.openweathermap.org/sample/city.list.json.gz")]
    (let [lines (line-seq (io/reader (GZIPInputStream. input)))]
      (group-by #(get % "country") (map json/parse-string lines)))))

(defn get-weather [path params]
  (let [url (str "http://api.openweathermap.org/data/" path)
        params (assoc params :appid (env :weather-appid))]
    (:body
      (http/get url {:query-params params :as :json}))))

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(def loading-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     mount-target
     (include-js "http://code.highcharts.com/adapters/standalone-framework.js")
     (include-js "http://code.highcharts.com/highcharts.js")
     (include-js "js/app.js")]]))


(defroutes routes
  (GET "/" [] loading-page)
  (GET "/weather" [] loading-page)

  (GET "/api/countries/" [] (response countries))
  (GET "/api/cities/:country" [country] (response (or (get cities country) [])))
  (GET "/data/*" request (response (get-weather (-> request :route-params :*)
                                                (-> request :query-params))))
  
  (resources "/")
  (not-found "Not Found"))

(def app
  (let [handler (-> #'routes
                    (wrap-json-response)
                    (wrap-defaults site-defaults))]
    (if (env :dev) (-> handler wrap-exceptions wrap-reload) handler)))
