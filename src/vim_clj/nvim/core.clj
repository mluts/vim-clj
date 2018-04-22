(ns vim-clj.nvim.core
  (:require [neovim-client.1.api :as nvim-api]
            [neovim-client.nvim :as nvim-client]
            [neovim-client.message :as msg]))

(defonce nvim (atom {}))

(defonce is-running-var "vim_clj_is_running")
(defonce channel-var "vim_clj_channel")

(defn msg->map [msg]
  {:msg-type  (msg/msg-type msg)
   :id        (msg/id msg)
   :method    (msg/method msg)
   :args      (msg/params msg)})

(defn register-method! [m f]
  (nvim-client/register-method! @nvim m f))

(defn command [& args]
  (apply nvim-api/command @nvim args))

(defn command-async [& args]
  (apply nvim-api/command-async @nvim args))

(defn call-function [& args]
  (apply nvim-api/call-function @nvim args))

(defn call-function-async [& args]
  (apply nvim-api/call-function-async @nvim args))

(defn set-var [name val]
  (nvim-api/set-var @nvim name val))

(defn set-var-async [name val cb]
  (nvim-api/set-var-async @nvim name val cb))

(defn get-var [name]
  (nvim-api/get-var @nvim name))

(defn connect-to-stdin! []
  (reset! nvim (nvim-client/new 1)))

(defn connect-to-port! [port]
  (reset! nvim (nvim-client/new 1 "127.0.0.1" (int port))))

(defn set-is-running [val]
  (nvim-api/set-var-async @nvim is-running-var (int val) (fn [& _])))

(defn set-channel-var []
  (nvim-api/get-api-info-async @nvim (fn [[channel]] (set-var-async channel-var channel (fn [& _])))))

(defn out-write [msg]
  (nvim-api/out-write @nvim msg))

(defn out-writeln [msg]
  (nvim-api/out-write @nvim (str msg "\n")))

(defn get-history [hist-name]
  {:pre [(string? hist-name)]}
  (let [first-hist-n 1
        last-hist-n (call-function "histnr" [hist-name])
        get-hist-call (fn [n] ["nvim_call_function" ["histget" [hist-name n]]])
        [results error] (nvim-api/call-atomic @nvim (map get-hist-call (range first-hist-n (inc last-hist-n))))]
    (if error
      (throw (Exception. (str "Failed to get history for " hist-name ". " (last error))))
      results)))

(defn reset-history [hist-name hist-seq]
  (let [[_ error] (nvim-api/call-atomic @nvim
                                              (cons ["nvim_call_function" ["histdel" [hist-name]]]
                                                    (map (fn [entry]
                                                           ["nvim_call_function" ["histadd" [hist-name entry]]]) hist-seq)))]
    (when error
      (throw (Exception. (str "Failed to reset history for " hist-name ". " (last error)))))))

(defn swap-history [hist-name hist-seq]
  (let [old-history (get-history hist-name)]
    (reset-history hist-name hist-seq)
    old-history))

(defn with-history [hist-name hist-seq f]
  (let [old-history (swap-history hist-name hist-seq)
        res (f)]
    (reset-history hist-name old-history)
    res))

(defn read-input [prompt]
  (call-function "input" [prompt]))

(defn read-input-cmline [prompt]
  (let [cedit (nvim-api/get-option @nvim "cedit")
        [results error] (nvim-api/call-atomic @nvim [["nvim_input" [cedit]]
                                                     ["nvim_call_function" ["input" [prompt]]]])]
    (when-not error
      (second results))))
