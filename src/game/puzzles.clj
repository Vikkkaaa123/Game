(ns game.puzzles
  "Логика головоломок игры"
  (:require [game.world :as world]
            [game.items :as items]
            [game.players.inventory :as inventory]))

;; Состояние головоломок
(defonce puzzles-state
  (atom {
    :microscope-puzzle {:solved false :code-found false}
    :code-lock {:solved false :attempts 0}
    :find-date {:solved false :date-found false}
    :repair-console {:solved false :steps-completed #{}}
    :final {:solved false}
  }))

;; ========== МИКРОСКОП ГОЛОВОЛОМКА ==========

(defn examine-microscope
  "Осмотр микроскопа"
  [player-name]
  (let [has-microscope (inventory/has-item? player-name "microscope")]
    (if has-microscope
      (do
        (swap! puzzles-state assoc-in [:microscope-puzzle :code-found] true)
        {:success true
         :message "🔬 В микроскопе вы видите слайд с надписью: '3XX7'\nПодсказка: X = номер месяца основания лаборатории"})
      {:error true
       :message "У вас нет микроскопа"})))

;; ========== КОДОВЫЙ ЗАМОК ==========

(defn try-code-lock
  "Попытка ввести код"
  [player-name code]
  (let [state (get @puzzles-state :code-lock)
        attempts (:attempts state)]
    
    (if (>= attempts 3)
      {:error true :message "Превышено количество попыток!"}
      
      (do
        (swap! puzzles-state update-in [:code-lock :attempts] inc)
        
        (if (= code "3107")
          (do
            (swap! puzzles-state assoc-in [:code-lock :solved] true)
            {:success true
             :message "✅ Код принят! Дверь в архив открыта!"})
          {:error true
           :message (str "❌ Неверный код. Попыток осталось: " (- 2 attempts))})))))

;; ========== ПОИСК ДАТЫ ==========

(defn find-lab-date
  "Поиск даты основания лаборатории"
  [player-name]
  (let [has-journal (inventory/has-item? player-name "journal")
        has-formulas (inventory/has-item? player-name "formulas")]
    
    (cond
      has-journal
      (do
        (swap! puzzles-state assoc-in [:find-date :date-found] true)
        (swap! puzzles-state assoc-in [:find-date :solved] true)
        {:success true
         :message "📖 В журнале вы нашли: 'Лаборатория основана в октябре 1997 года (месяц 10)'"})
      
      has-formulas
      (do
        (swap! puzzles-state assoc-in [:find-date :date-found] true)
        {:success true
         :message "📄 В формулах вы нашли код: '3107' в углу страницы"})
      
      :else
      {:error true
       :message "У вас нет документов для поиска даты"})))

;; ========== ПОЧИНКА КОНСОЛИ ==========

(defn repair-console
  "Починка консоли управления"
  [player-name]
  (let [has-wire (inventory/has-item? player-name "wire")
        has-blueprint (inventory/has-item? player-name "blueprint")
        state (get @puzzles-state :repair-console)]
    
    (cond
      (not has-wire)
      {:error true :message "❌ Нужен провод для починки"}
      
      (not has-blueprint)
      {:error true :message "❌ Нужна схема подключения"}
      
      :else
      (do
        (swap! puzzles-state update-in [:repair-console :steps-completed] conj :repaired)
        (swap! puzzles-state assoc-in [:repair-console :solved] true)
        
        {:success true
         :message (str "🔧 Вы починили консоль! Шаги выполнены:\n"
                       "1. Подключили провод ✓\n"
                       "2. Следовали схеме ✓\n"
                       "3. Введите код активации")}))))

;; ========== ПРОВЕРКА ПОБЕДЫ ==========

(defn check-escape
  "Проверка условий победы"
  [player-name]
  (let [puzzles @puzzles-state
        all-solved (and
                    (get-in puzzles [:microscope-puzzle :code-found])
                    (get-in puzzles [:code-lock :solved])
                    (get-in puzzles [:find-date :solved])
                    (get-in puzzles [:repair-console :solved]))]
    
    (if all-solved
      (do
        (swap! puzzles-state assoc-in [:final :solved] true)
        {:success true
         :message "🎉 ПОЗДРАВЛЯЕМ! ВЫ ВЫБРАЛИСЬ ИЗ ЛАБОРАТОРИИ!\n\n"})
      {:error true
       :message "Еще не все головоломки решены"})))

;; ========== КОМАНДЫ ДЛЯ ГОЛОВОЛОМОК ==========

(defn handle-puzzle-command
  "Обработка команд головоломок"
  [player-name command args]
  (case command
    "осмотреть микроскоп" (examine-microscope player-name)
    "ввести код" (try-code-lock player-name args)
    "найти дату" (find-lab-date player-name)
    "починить консоль" (repair-console player-name)
    "побег" (check-escape player-name)
    {:error true :message "Неизвестная команда головоломки"}))

;; Инициализация
(println "[PUZZLES] Система головоломок загружена")