# Authenticated web endpoints

GET /api/me/accounts returns accounts for the verified JWT subject for ADMIN, REVIEWER and MEMBER. A userId query parameter does not change ownership. Existing staff queries and ADMIN account creation remain in force. No new secret or database migration is required.
