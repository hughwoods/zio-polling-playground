# ZIO Polling Playground

## A project exploring ZIO schedules. 

We have a `Client` which allows us to schedule some work to be done by an external service, and to check the status of work to see if it is completed. 
Our app should:
1. Schedule work.
2. Poll the client until either the work is done, or the service responds with some non-transient fault. 
3. In the case of a transient fault, we should try again up to 3 times.
4. If the work has not completed after some amount of time, we should send a cancellation request to the service & then exit.