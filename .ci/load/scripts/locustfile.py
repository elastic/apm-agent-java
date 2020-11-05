from locust import HttpUser, task, constant_pacing


class QuickstartUser(HttpUser):
    wait_time = constant_pacing(1)

    @task
    def index_page(self):
        self.client.get("/owners/find")

    @task(3)
    def gen_error(self):
        self.client.get("/oups")
