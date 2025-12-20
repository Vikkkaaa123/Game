(defproject mire "0.0.1"
  :description "Кооперативная текстовая игра 'Побег из лаборатории' с STM"
  :url "https://github.com/your-org/escape-game"
  
  :dependencies [
    ;; Clojure
    [org.clojure/clojure "1.11.1"]
    
    ;; Оригинальные зависимости Mire
    [org.clojure/tools.cli "1.0.219"]
    
    ;; Для веб-сервера (Человек 3)
    [org.httpkit/httpkit "2.8.0"]
    [compojure "1.7.0"]
    [ring/ring-core "1.11.0"]
    [ring/ring-defaults "0.4.0"]
    [cheshire "5.12.0"]  ;; JSON парсинг
    
    ;; Утилиты
    [clojure.java-time "1.4.2"]  ;; Работа со временем
    [org.clojure/tools.logging "1.3.0"]  ;; Логирование
  ]
  
  ;; Исходные пути - ВАЖНО! Добавляем путь к нашей игре
  :source-paths ["src" "src/game"]
  
  ;; Ресурсы
  :resource-paths ["resources" "resources/rooms" "resources/items"]
  
  ;; Главный класс для запуска
  :main game.core
  
  ;; Плагины
  :plugins [[lein-ring "0.12.6"]]
  
  ;; Профили
  :profiles {
    :dev {
      :dependencies [
        ;; Для тестирования в REPL
        [org.clojure/tools.namespace "1.4.4"]
        [criterium "0.4.6"]
      ]
      :source-paths ["dev"]
    }
    
    :uberjar {
      :aot :all
      :main game.core
      :uberjar-name "escape-game-standalone.jar"
    }
    
    :test {
      :dependencies [
        [org.clojure/test.check "1.1.1"]
      ]
    }
  }
  
  ;; Настройки для Uberjar
  :uberjar {
    :exclusions [#"\.jar$"]
  }
  
  ;; Настройки сборки
  :min-lein-version "2.9.0"
  
  ;; JVM настройки
  :jvm-opts [
    "-server"
    "-Xmx2g"
    "-XX:+UseG1GC"
    "-Dfile.encoding=UTF-8"
  ]
  
  ;; Репозитории
  :repositories [
    ["central" {:url "https://repo1.maven.org/maven2/"}]
    ["clojars" {:url "https://repo.clojars.org/"}]
  ]
  
  ;; Документация
  :scm {:name "git"
        :url "https://github.com/your-org/escape-game"}
  
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  
  ;; Тестирование
  :test-selectors {
    :default (constantly true)
    :unit :unit
    :integration :integration
    :stm :stm
  }
  
  :aliases {
    ;; Запуск игры
    "run-game" ["run" "-m" "game.core"]
    
    ;; Запуск тестов
    "test-all" ["do" "test"]
    
    ;; Создание Uberjar
    "build" ["uberjar"]
    
    ;; Запуск REPL с загруженными зависимостями
    "repl" ["do" "clean," "repl"]
    
    ;; Проверка стиля кода
    "lint" ["do" "eastwood," "kibit"]
    
    ;; Запуск с тестовым режимом
    "test-run" ["run" "-m" "game.core" "--test"]
  }
  
  ;; Настройки Eastwood (линтер)
  :eastwood {
    :config-files ["eastwood-config.clj"]
    :exclude-linters [:unused-ret-vals]
  }
  
  ;; Подсказки для IDE
  :lein-tools-deps/config {
    :aliases {:dev {:extra-paths ["dev"]}}
  }
)
