(defproject mire "0.0.1"
  :description "Кооперативная текстовая игра 'Побег из лаборатории' на Clojure с STM"
  :url "https://github.com/Vikkkaaa123/Game"
  
  :dependencies [
    ;; Clojure
    [org.clojure/clojure "1.11.1"]
    
    ;; Оригинальные зависимости Mire
    [org.clojure/tools.cli "1.0.219"]
    [server-socket "1.0.0"]
    
    ;; Для JSON (веб-интерфейс)
    [cheshire "5.12.0"]

    [http-kit "2.7.0"]
    [compojure "1.6.2"]
    [ring/ring-core "1.12.0"]
    [ring/ring-defaults "0.4.0"]
    [ring/ring-json "0.5.1"]

    [org.clojure/tools.logging "1.2.4"]
    [ch.qos.logback/logback-classic "1.4.14"]
    
    ;; Для JSON
    [cheshire "5.12.0"]
    [ring-cors "0.1.13"]
    
    ;; Для шаблонов
    [hiccup "2.0.0-RC2"]

    [org.clojure/clojure "1.11.1"]
    [org.clojure/tools.cli "1.0.219"]
    [server-socket "1.0.0"]
    [http-kit "2.7.0"]
    [cheshire "5.12.0"]
    [compojure "1.6.2"]
    [ring/ring-core "1.12.0"]
    [ring/ring-defaults "0.4.0"]
    [org.clojure/tools.logging "1.2.4"]

    [server-socket "1.0.0"] ; Для telnet-сервера
    [http-kit "2.5.3"]      ; Для веб-сервера[citation:1][citation:5]
    [cheshire "5.12.0"]     ; Для работы с JSON
    [compojure "1.6.2"]
  ]
  
  ;; Исходные пути - ОЧЕНЬ ВАЖНО!
  :source-paths ["src" "src/mire" "src/game"]
  
  ;; Ресурсы
  :resource-paths ["resources"]
  
  ;; Главный класс для запуска
  :main game.server.unified
  
  ;; Минимальная версия Leiningen
  :min-lein-version "2.0.0"
  
  ;; Плагины
  :plugins [[lein-ring "0.12.6"]]
  
  ;; Профили
  :profiles {
    :dev {
      :dependencies [
        ;; Для тестирования
        [org.clojure/test.check "1.1.1"]
        [criterium "0.4.6"]
      ]
      :source-paths ["dev"]
    }
    
    :uberjar {
      :aot :all
      :main mire.server
      :uberjar-name "mire-standalone.jar"
      :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
    }
  }
  
  ;; JVM настройки для поддержки русского языка
  :jvm-opts [
    "-server"
    "-Xmx1g"
    "-XX:+UseG1GC"
    "-Dfile.encoding=UTF-8"
    "-Dconsole.encoding=UTF-8"
    "-Dsun.jnu.encoding=UTF-8"
    "-Djava.awt.headless=true"
  ]
  
  ;; Алиасы для удобства
  :aliases {
    ;; Запуск игры
    "run-game" ["run" "-m" "mire.server"]
    
    ;; Запуск с тестовыми данными
    "run-test" ["run" "-m" "mire.server" "--test"]
    
    ;; Создание Uberjar
    "build" ["do" "clean," "uberjar"]
    
    ;; Тесты
    "test-all" ["do" "test"]
    
    ;; REPL с загруженными зависимостями
    "repl-dev" ["do" "clean," "repl"]
  }
  
  ;; Репозитории
  :repositories [
    ["central" {:url "https://repo1.maven.org/maven2/"}]
    ["clojars" {:url "https://repo.clojars.org/"}]
  ]
  
  ;; Лицензия
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  
  ;; Для Emacs/CIDER
  :cider {
    :nrepl-middleware ["cider.nrepl/cider-middleware"]
  }
)
