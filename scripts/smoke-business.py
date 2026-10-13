"""Business API smoke test. Creates disposable users/products/orders; use a test database.

Python 3 standard library only. Set BOOTSTRAP_ADMIN_USERNAME/PASSWORD in the environment.
This does not call a payment success endpoint or an external payment channel.
"""
import argparse
import json
import os
import secrets
import urllib.error
import urllib.request
import uuid


def run(base):
    def request(path, method="GET", body=None, token=None, expected=200, headers=None):
        hdr = {"Content-Type": "application/json", **(headers or {})}
        if token:
            hdr["Authorization"] = "Bearer " + token
        req = urllib.request.Request(base.rstrip("/") + "/api" + path,
            data=None if body is None else json.dumps(body).encode(), headers=hdr, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            payload = json.load(response)
            assert response.status == expected, (method, path, response.status, payload.get("code"), payload.get("message"))
        if expected == 200:
            assert payload["code"] == "OK", payload.get("message")
        return payload.get("data")

    suffix = uuid.uuid4().hex[:12]
    username = "qa_" + suffix
    password = secrets.token_urlsafe(24)
    buyer = request("/auth/register", "POST", {"username": username, "password": password, "role": "ADMIN"})
    assert buyer["role"] == "USER" and isinstance(buyer["userId"], str)
    token = request("/auth/login", "POST", {"username": username, "password": password})["accessToken"]
    me = request("/users/me", token=token, headers={"X-User-Id": "1", "X-User-Role": "ADMIN"})
    assert me["userId"] == buyer["userId"] and me["role"] == "USER"
    request("/users/me", expected=401, headers={"X-User-Id": "1"})
    request("/admin/products", token=token, expected=403)
    print("PASS database registration/login, string IDs, identity and admin isolation")

    admin = request("/auth/login", "POST", {"username": os.environ["BOOTSTRAP_ADMIN_USERNAME"],
        "password": os.environ["BOOTSTRAP_ADMIN_PASSWORD"]})["accessToken"]
    product_body = {"name": "业务验证 " + suffix, "skuName": "验证规格", "priceCent": 12345, "onShelf": False}
    product = request("/admin/products", "POST", product_body, admin)
    sku = product["skuId"]
    assert isinstance(sku, str)
    request("/skus/" + sku, expected=400)
    request("/admin/inventory/" + sku, "PUT", {"total": 5}, admin)
    product_body["onShelf"] = True
    request("/admin/products/" + sku, "PUT", product_body, admin)
    assert request("/skus/" + sku)["priceCent"] == 12345
    assert request("/products?keyword=" + suffix)[0]["skuId"] == sku
    print("PASS administrator product create, stock, shelf and customer catalog")

    items = {"items": [{"skuId": sku, "quantity": 2}]}
    assert request("/orders/settlement", "POST", items, token)["totalAmountCent"] == 24690
    hdr = {"X-Idempotency-Token": uuid.uuid4().hex}
    order = request("/orders", "POST", items, token, headers=hdr)
    retry = request("/orders", "POST", items, token, headers=hdr)
    assert retry["orderNo"] == order["orderNo"]
    assert isinstance(order["items"][0]["skuId"], str)
    no = order["orderNo"]
    assert request("/admin/inventory/" + sku, token=admin)["reserved"] == 2
    request("/orders", "POST", {"items": [{"skuId": sku, "quantity": 1}]}, token, expected=400, headers=hdr)
    other = request("/auth/register", "POST", {"username": "other_" + suffix, "password": password})["accessToken"]
    request("/orders/" + no, token=other, expected=400)
    request("/admin/order-operations/" + no + "/deliver?userId=" + buyer["userId"], "POST", token=token, expected=403)
    detail = request("/admin/order-operations/" + no + "?userId=" + buyer["userId"], token=admin)
    assert detail["orderNo"] == no and detail["status"] == "WAIT_PAY"
    print("PASS server quote, stock reservation, durable retry, ownership and administrator detail")

    methods = request("/payments/methods", token=token)
    assert {"ALIPAY_PAGE", "WECHAT_NATIVE"}.issubset({m["code"] for m in methods})
    assert not any(m["available"] for m in methods if m["code"] in {"ALIPAY_PAGE", "WECHAT_NATIVE"})
    request("/orders/" + no + "/pay", "POST", {"methodCode": "ALIPAY_PAGE"}, token, expected=400)
    request("/orders/" + no + "/cancel", "POST", token=token)
    assert request("/orders/" + no, token=token)["status"] == "CANCELED"
    request("/admin/products/" + sku, "PUT", {**product_body, "onShelf": False}, admin)
    print("PASS multi-method availability, unavailable method rejection, cancellation")
    print("Business API smoke passed. RabbitMQ delivery, paid fulfillment and real payment are separate checks.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    run(parser.parse_args().base_url)
