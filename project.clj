(defproject mire "0.0.1"
  :description "Кооперативная текстовая игра 'Побег из лаборатории' на Clojure с STM"
  :url "https://github.com/Vikkkaaa123/Game"
  
  :dependencies [
    ;; Clojure
    [org.clojure/clojure "1.11.1"]
    
    ;; Оригинальные зависимости Mire
    [org.clojure/tools.cli "1.0.219"]
    
    ;; Для WebSocket сервера (человек 3)
    [http-kit "2.7.0"]
    
    ;; Для JSON (веб-интерфейс)
    [cheshire "5.12.0"]
  ]
  
  ;; Исходные пути - ОЧЕНЬ ВАЖНО!
  :source-paths ["src" "src/mire" "src/game"]
  
  ;; Ресурсы
  :resource-paths ["resources"]
  
  ;; Главный класс для запуска
  :main mire.server
  
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
