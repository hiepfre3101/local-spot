-- Giới hạn tần suất cửa sổ trượt (sliding window log) — chạy nguyên tử trong Redis (một script = một thao tác).
-- Sorted set: mỗi lần được phép là một phần tử, score = thời điểm (ms). Lần bị từ chối KHÔNG được ghi lại,
-- nên kẻ tấn công không tự kéo dài thời gian bị chặn của người khác, và tối đa đúng `limit` lần trong mọi `window`.
--
-- KEYS[1] = khóa bộ đếm
-- ARGV[1] = now (ms), ARGV[2] = window (ms), ARGV[3] = limit, ARGV[4] = member duy nhất cho lần này
-- Trả về { 1, 0 } nếu được phép; { 0, retryAfterMs } nếu vượt ngưỡng.

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

-- Bỏ các lần đã ra khỏi cửa sổ
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

if redis.call('ZCARD', key) < limit then
    redis.call('ZADD', key, now, ARGV[4])
    -- Khóa tự hết hạn khi không còn lần nào trong cửa sổ — không tích rác trong Redis
    redis.call('PEXPIRE', key, window)
    return { 1, 0 }
end

-- Vượt ngưỡng: được thử lại khi lần cũ nhất trong cửa sổ hết hạn
local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
local retryAfter = tonumber(oldest[2]) + window - now
if retryAfter < 1 then
    retryAfter = 1
end
return { 0, retryAfter }
