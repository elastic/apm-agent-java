from locust import HttpUser, task, constant_pacing

class QuickstartUser(HttpUser):
    wait_time = constant_pacing(1)

    # note: this URL is a form, thus doing a POST request with a search would be more appropriate
    @task
    def owners_find(self):
        self.client.get("/owners/find")

    @task
    def owners_list(self):
        self.client.get("/owners")

    @task(3)
    def gen_error(self):
        # because 500 error is expected, we treat any other result as failure
        with self.client.get("/oups", catch_response=True) as response:
            if response.status_code == 500:
                response.success()
            else:
                response.failure('unexpected status code = {}'.format(response.status_code))
