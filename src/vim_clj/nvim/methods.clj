(ns vim-clj.nvim.methods
  (:require [vim-clj.inspect :as inspect]
            [vim-clj.nvim.core :as nvim]
            [vim-clj.nrepl.core :as nrepl]
            [cljfmt.core :as fmt]))

(defonce should-shutdown (atom false))

(defn shutdown [& _]
  (reset! should-shutdown true))

(defn- escape-echo [msg]
  (str "'" (clojure.string/replace msg "'" "''") "'"))

(defn- echo-sync [& args]
  (nvim/command (str "echo " (escape-echo (apply str args)))))

(defn clj-file-ns [msg]
  (let [{:keys [args]} (nvim/msg->map msg)]
    (try
      (inspect/read-ns-name (first args))
      (catch Exception _ nil))))

(defn buf-ns-eval [msg]
  (let [{:keys [args]} (nvim/msg->map msg)]
    (future
      (let [fpath (nvim/call-function "expand" ["%"])
            code (first args)
            ns (inspect/read-ns-name fpath)
            eval-res (delay (nrepl/ns-eval ns code))
            eval-val (delay (->> @eval-res :value))
            eval-out (delay (->> @eval-res :out))]
        (when (and ns code @eval-res (or @eval-val @eval-out))
          (echo-sync (str @eval-out) (or @eval-val "nil")))))
    nil))

(defn format-code [msg]
  (let [{:keys [args]} (nvim/msg->map msg)
        code (first args)]
    (when code
      (try (fmt/reformat-string (str code))
           (catch Exception _ nil)))))

(defn register-methods! []
  (let [
        methods {"shutdown"     #'shutdown
                 "clj-file-ns"  #'clj-file-ns
                 "buf-ns-eval"  #'buf-ns-eval
                 "format-code"  #'format-code}]
    (doseq [[m f] methods] (nvim/register-method! m f))))
