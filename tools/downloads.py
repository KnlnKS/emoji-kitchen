import time

import httpx


def request(client, url, method="GET"):
    for attempt in range(4):
        try:
            response = client.request(method, url)
            response.raise_for_status()
            return response
        except httpx.HTTPError as error:
            if isinstance(error, httpx.HTTPStatusError) and error.response.status_code == 404:
                return None
            if attempt == 3:
                raise
        time.sleep(2 ** attempt)


def fetch_bytes(client, urls):
    for url in urls:
        response = request(client, url)
        if response is not None:
            return url, response.content
    return None
