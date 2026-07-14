-- V020: заменяем Company.owner (один владелец на компанию) и user_roles (Set<Role>)
-- на модель "scope прямо на аккаунте": role + company_id + restaurant_id на users.
-- ROLE_SUPER_ADMIN         -> company_id IS NULL, restaurant_id IS NULL (вся платформа)
-- ROLE_USER (клиент)       -> company_id IS NULL, restaurant_id IS NULL
-- ROLE_COMPANY_ADMIN       -> company_id задан, restaurant_id IS NULL (все рестораны компании)
-- ROLE_RESTAURANT_ADMIN    -> company_id и restaurant_id оба заданы (один конкретный ресторан)

ALTER TABLE users
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'ROLE_USER'
        CHECK (role IN ('ROLE_USER', 'ROLE_RESTAURANT_ADMIN', 'ROLE_COMPANY_ADMIN', 'ROLE_SUPER_ADMIN')),
    ADD COLUMN company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    ADD COLUMN restaurant_id BIGINT REFERENCES restaurants(id) ON DELETE SET NULL;

-- Текущий владелец компании становится её company-admin.
UPDATE users u
SET role = 'ROLE_COMPANY_ADMIN', company_id = c.id
FROM companies c
WHERE c.owner_user_id = u.id;

-- Кто уже имел ROLE_SUPER_ADMIN в старой Set<Role> модели - сохраняет её (и не может
-- одновременно быть company-admin, супер-админ вне компаний по определению).
UPDATE users u
SET role = 'ROLE_SUPER_ADMIN', company_id = NULL, restaurant_id = NULL
FROM user_roles ur
WHERE ur.user_id = u.id AND ur.role = 'ROLE_SUPER_ADMIN';

DROP TABLE user_roles;

ALTER TABLE companies DROP COLUMN owner_user_id;

ALTER TABLE users ADD CONSTRAINT chk_users_role_scope CHECK (
    (role = 'ROLE_SUPER_ADMIN'      AND company_id IS NULL     AND restaurant_id IS NULL) OR
    (role = 'ROLE_USER'             AND company_id IS NULL     AND restaurant_id IS NULL) OR
    (role = 'ROLE_COMPANY_ADMIN'    AND company_id IS NOT NULL AND restaurant_id IS NULL) OR
    (role = 'ROLE_RESTAURANT_ADMIN' AND company_id IS NOT NULL AND restaurant_id IS NOT NULL)
);

CREATE INDEX idx_users_company ON users(company_id);
CREATE INDEX idx_users_restaurant ON users(restaurant_id);
