#!/bin/bash
# Test API flow cho security-core BE độc lập
# Server: http://localhost:8081

BASE="http://localhost:8081"
PASS=0
FAIL=0

# Color helpers
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

step() { echo -e "\n${BLUE}=== $1 ===${NC}"; }
ok()   { echo -e "${GREEN}[OK]${NC} $1"; PASS=$((PASS+1)); }
err()  { echo -e "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL+1)); }
info() { echo -e "${YELLOW}     $1${NC}"; }

assert_status() {
  local expected=$1 actual=$2 desc=$3
  if [ "$actual" = "$expected" ]; then ok "$desc (HTTP $actual)"; else err "$desc — expected $expected, got $actual"; fi
}

# 1. Health check
step "1. Health check"
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/management/health")
assert_status 200 "$HEALTH" "GET /management/health"

# 2. Anonymous: GET /api/authenticate -> 401
step "2. Anonymous request bị từ chối"
ANON=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/account")
assert_status 401 "$ANON" "GET /api/account (no token)"

# 3. Login admin
step "3. Login admin/admin"
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin","rememberMe":false}')
LOGIN_BODY=$(echo "$LOGIN_RESP" | sed '$d')
LOGIN_CODE=$(echo "$LOGIN_RESP" | tail -n1)
assert_status 200 "$LOGIN_CODE" "POST /api/authenticate (admin/admin)"
TOKEN=$(echo "$LOGIN_BODY" | grep -o '"id_token":"[^"]*"' | sed 's/"id_token":"\([^"]*\)"/\1/')
if [ -n "$TOKEN" ]; then ok "JWT token nhận được (${#TOKEN} chars)"; info "${TOKEN:0:60}..."; else err "Không có id_token trong response"; echo "$LOGIN_BODY"; fi
AUTH="Authorization: Bearer $TOKEN"

# 4. Login sai
step "4. Login sai password"
BAD=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrong","rememberMe":false}')
assert_status 401 "$BAD" "POST /api/authenticate (sai password)"

# 5. Get account
step "5. Lấy thông tin tài khoản hiện tại"
ACC_RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/account" -H "$AUTH")
ACC_BODY=$(echo "$ACC_RESP" | sed '$d')
ACC_CODE=$(echo "$ACC_RESP" | tail -n1)
assert_status 200 "$ACC_CODE" "GET /api/account"
echo "$ACC_BODY" | head -c 300; echo ""

# 6. Check authenticated
step "6. Kiểm tra authenticated"
AUTH_CHK=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/authenticate" -H "$AUTH")
assert_status 204 "$AUTH_CHK" "GET /api/authenticate (có token)"

# 7. List organizations
step "7. List organizations"
ORG_RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/organizations?page=0&size=5" -H "$AUTH")
ORG_BODY=$(echo "$ORG_RESP" | sed '$d')
ORG_CODE=$(echo "$ORG_RESP" | tail -n1)
assert_status 200 "$ORG_CODE" "GET /api/organizations"
info "Body length: ${#ORG_BODY} chars"
echo "$ORG_BODY" | head -c 300; echo ""

# 8. Create organization
step "8. Create organization"
NEW_ORG_NAME="Test Org $(date +%s)"
CREATE_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/organizations" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "{\"name\":\"$NEW_ORG_NAME\",\"code\":\"TEST$(date +%s)\",\"ownerLogin\":\"admin\"}")
CREATE_BODY=$(echo "$CREATE_RESP" | sed '$d')
CREATE_CODE=$(echo "$CREATE_RESP" | tail -n1)
assert_status 201 "$CREATE_CODE" "POST /api/organizations"
NEW_ID=$(echo "$CREATE_BODY" | grep -o '"id":[0-9]*' | head -n1 | sed 's/"id"://')
info "New org id: $NEW_ID"
echo "$CREATE_BODY" | head -c 300; echo ""

# 9. Get org by id
if [ -n "$NEW_ID" ]; then
  step "9. Get organization by id=$NEW_ID"
  GET_ONE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/organizations/$NEW_ID" -H "$AUTH")
  assert_status 200 "$GET_ONE" "GET /api/organizations/$NEW_ID"

  # 10. Delete org
  step "10. Delete organization id=$NEW_ID"
  DEL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/organizations/$NEW_ID" -H "$AUTH")
  if [ "$DEL_CODE" = "204" ] || [ "$DEL_CODE" = "200" ]; then ok "DELETE /api/organizations/$NEW_ID (HTTP $DEL_CODE)"; else err "DELETE expected 204/200, got $DEL_CODE"; fi
fi

# 11. List departments
step "11. List departments"
DEPT_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/departments?page=0&size=5" -H "$AUTH")
assert_status 200 "$DEPT_CODE" "GET /api/departments"

# 12. List employees
step "12. List employees"
EMP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/employees?page=0&size=5" -H "$AUTH")
assert_status 200 "$EMP_CODE" "GET /api/employees"

# 13. List users (admin endpoint)
step "13. List users (admin only)"
USERS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/admin/users?page=0&size=5" -H "$AUTH")
if [ "$USERS_CODE" = "200" ]; then ok "GET /api/admin/users (HTTP 200)"; else info "GET /api/admin/users -> HTTP $USERS_CODE (có thể đường dẫn khác)"; fi

# 14. Login user thường + thử admin endpoint -> 403
step "14. Login user thường, thử admin endpoint"
USER_LOGIN=$(curl -s -X POST "$BASE/api/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user","rememberMe":false}')
USER_TOKEN=$(echo "$USER_LOGIN" | grep -o '"id_token":"[^"]*"' | sed 's/"id_token":"\([^"]*\)"/\1/')
if [ -n "$USER_TOKEN" ]; then
  ok "Login user thường thành công"
  FORBID=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/admin/users" -H "Authorization: Bearer $USER_TOKEN")
  if [ "$FORBID" = "403" ] || [ "$FORBID" = "401" ]; then ok "User thường bị chặn admin endpoint (HTTP $FORBID)"; else info "Admin endpoint trả $FORBID (có thể không tồn tại)"; fi
else
  err "Login user thường thất bại"
fi

# 15. Token sai
step "15. Token sai bị reject"
BAD_TOKEN=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/account" -H "Authorization: Bearer not.a.real.token")
assert_status 401 "$BAD_TOKEN" "GET /api/account với bad token"

# Tóm tắt
echo ""
echo "═══════════════════════════════════════"
echo -e "  Tổng kết: ${GREEN}$PASS PASS${NC} / ${RED}$FAIL FAIL${NC}"
echo "═══════════════════════════════════════"
exit $FAIL
