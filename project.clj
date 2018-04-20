(defproject vim-nrepl "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [neovim-client "0.1.2"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [cljfmt "0.5.7"]]
  :main ^:skip-aot vim-nrepl.core
  :target-path "target/%s"
  :uberjar-name "vim-nrepl.jar"
  :repl-options {:init-ns vim-nrepl.repl}
  :profiles {:uberjar {:aot :all}})
