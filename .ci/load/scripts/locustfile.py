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
                response.failure()

@events.quitting.add_listener
def _(environment, **kw):
    if environment.stats.total.fail_ratio > 30:
        logging.error("Test failed due to failure ratio > 30%")
        environment.process_exit_code = 1
    elif environment.stats.total.avg_response_time > 200:
        logging.error("Test failed due to average response time ratio > 200 ms")
        environment.process_exit_code = 1
    elif environment.stats.total.get_response_time_percentile(0.95) > 800:
        logging.error("Test failed due to 95th percentile response time > 800 ms")
        environment.process_exit_code = 1
    else:
        environment.process_exit_code = 0
