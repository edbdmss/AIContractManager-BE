CREATE UNIQUE INDEX uk_users_provider_user
    ON users(provider, provider_user_id);
