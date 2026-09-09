import httpx
import time

while(True):

    response = httpx.get("http://localhost:8080/worker/ping/74e06875-91c9-4202-8b69-73cc14f8a61b")
    print(response.json())
    time.sleep(5)