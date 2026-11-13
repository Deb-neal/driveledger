import os

bind = "0.0.0.0:" + os.environ.get("PORT", "10000")
# One worker shares the in-flight lock; SQLite quotas remain atomic across restarts.
workers = 1
worker_class = "gthread"
threads = 4
timeout = 30
graceful_timeout = 20
keepalive = 2
limit_request_line = 2048
limit_request_fields = 30
limit_request_field_size = 4096
accesslog = None
loglevel = "error"
capture_output = False
