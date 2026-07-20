-- V021: причина отклонения ресторана при модерации (PENDING_MODERATION -> HIDDEN)
-- была связана только с audit_log (details, свободный текст) без возможности прочитать
-- её обратно через API ресторана. Добавляем поле на саму сущность.

ALTER TABLE restaurants ADD COLUMN rejection_reason TEXT;
