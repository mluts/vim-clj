(ns vim-clj.nrepl.core
  (:require [clojure.tools.nrepl :as nrepl]
            [vim-clj.nvim.core :as nvim]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defonce connections (atom {}))

(defonce nrepl-connection (atom nil))

(def ^:const portfile ".nrepl-port")

(defn- parse-int [str]
  (try (Integer/parseInt str)
       (catch NumberFormatException _ 0)))

(defn- nrepl-connect [port & {:keys [host] :or {host "localhost"}}]
  (let [client (-> (nrepl/connect :host host :port port)
                   (nrepl/client 5000))
        session (nrepl/new-session client)]
    (nrepl/client-session client :session session)))

(defn port-from-portfile []
  (let [file (io/as-file portfile)
        port (delay (parse-int (slurp portfile)))]
    (when (and (.canRead file) (< 0 @port))
      @port)))

(defn str->conn-map [conn-str]
  (let [conn-array (string/split conn-str #":" 2)
        [host port] conn-array]
    (cond
      (and (= 2 (count conn-array))
           (< 0 (parse-int port))) {:host host :port (parse-int port)}
      (and (= 1 (count conn-array))
           (< 0 (parse-int host))) {:port (parse-int host)})))

(defn- ask-nrepl-address []
  (nvim/call-function ["Nrepl <port> or <host:port>: "]))

(defn conn-map-from-user-input []
  (str->conn-map (ask-nrepl-address)))

(defn new-connection []
  (let [portfile-port (delay (port-from-portfile))
        conn-map (delay (conn-map-from-user-input))]
    (cond
      @portfile-port (nrepl-connect @portfile-port)
      (and @conn-map
           (:host @conn-map)
           (:port @conn-map)) (nrepl-connect (:port @conn-map) (:host @conn-map))
      (and @conn-map
           (:port @conn-map)) (nrepl-connect (:port @conn-map)))))

(defn- alive? [_conn] true)

(defn get-nrepl-connection []
  (swap! nrepl-connection #(if (and % (alive? %)) % (new-connection)))
  @nrepl-connection)

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
