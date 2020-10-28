from locust import HttpUser, task, between


class QuickstartUser(HttpUser):
    wait_time = between(1, 2)

    @task
    def index_page(self):
        self.client.get("/owners/find")

    @task(3)
    def gen_error(self):
        self.client.get("/oups")
