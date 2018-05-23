(ns vim-clj.nrepl.core
  (:require [clojure.tools.nrepl :as nrepl]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defonce connections (atom {}))
(defonce default-scope "_default")

(def ^:dynamic *connection-scope* default-scope)

(def ^:const portfile ".nrepl-port")
(def ^:const nrepl-client-timeout 5000)

(defn- parse-int [str]
  (try (Integer/parseInt str)
       (catch NumberFormatException _ 0)))

(defn- nrepl-connect [port & {:keys [host] :or {host "localhost"}}]
  (let [client (-> (nrepl/connect :host host :port port)
                   (nrepl/client 5000))
        session (nrepl/new-session client)]
    (nrepl/client-session client :session session)))

(defn port-from-portfile
  ([] (->> (map port-from-portfile [(str *connection-scope* "/" portfile)
                                    portfile])
           (filter identity)
           first))
  ([path]
   (let [file (io/as-file path)
         port (delay (parse-int (slurp path)))]
     (when (and (.canRead file) (< 0 @port))
       @port))))

(defn str->conn-map [conn-str]
  (let [[addr scope]    (str/split conn-str #" " 2)
        [host port]  (str/split addr #":" 2)
        port-int      (delay (parse-int port))
        host-as-int   (delay (parse-int host))
        {:keys [port] :as conn-map} (cond
                                      (and host port scope) {:host host
                                                             :port @port-int
                                                             :scope scope}
                                      (and host scope)      {:port @host-as-int
                                                             :scope scope})]
    (when (and port (< 0 port)) conn-map)))

(defn port->conn-map [port]
  {:port port})

(defn conn-map->conn [{:keys [host port]}]
  (nrepl-connect port :host host))

(defn auto-connect! []
  (let [port (port-from-portfile)
        conn-map (delay (port->conn-map port))]
    (when (and port @conn-map)
      (conn-map->conn @conn-map))))

(defn manual-connect! [{:keys [scope] :as conn-map}]
  (when-let [conn (conn-map->conn conn-map)]
    (swap! connections assoc scope conn)
    conn))

(defn- alive? [_conn] true)

(defn get-nrepl-connection []
  (let [conns (swap! connections (fn [m]
                                   (let [conn (get m *connection-scope*)]
                                     (if (and conn (alive? conn))
                                       m
                                       (assoc m *connection-scope* (auto-connect!))))))]
    (get conns *connection-scope*)))

(defn message [msg]
  (when-let [client (get-nrepl-connection)]
    (nrepl/combine-responses (nrepl/message client msg))))

(defn raw-eval [code]
  (message {:op "eval" :code code}))

(defn raw-eval-pprint [code]
  (message {:op "eval" :pprint "" :code code}))

(defn ns-eval [ns code]
  (raw-eval (format "(do (clojure.core/require '%s) (in-ns '%s) %s)" ns ns code)))

(defn ns-eval-pprint [ns code]
  (raw-eval-pprint (format "(do (clojure.core/require '%s) (in-ns '%s) %s)" ns ns code)))

(defn format-code [code]
  (message {:op "format-code" :code code}))

(defn describe []
  (message {:op "describe"}))

(defn symbol-info [ns symbol]
  (message {:op "info" :ns ns :symbol symbol}))

(defn has-op? [op]
  (when-let [res (describe)]
    (boolean (get-in res [:ops op]))))

(defn- file-str->map [file-str]
  (let [[f-type tail] (str/split file-str #":" 2)]
    (if (= "jar" f-type)
      (let [[file-str entry] (str/split tail #"!" 2)]
        (assoc (file-str->map file-str)
               :entry entry))
      {:file tail})))

(defn symbol-info->location [{:keys [file column line]}]
  (when file
    (assoc (file-str->map file)
          :column column
          :line line)))
