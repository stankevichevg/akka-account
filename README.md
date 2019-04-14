# Project

Implementation of the RESTful API (including data model and the backing implementation) for money transfers between accounts.

To serve RESTful API application uses akka-http library with it's routing DSL.

To implement consistent transfers Akka persistent actors was used. So, the application demonstrates event-sourced way to implement the task using actors.

# Tasks

## Build and run

Application is distributed along with the gradle wrapper to make it easy to build and run.

To start the server just type `./gradlew run`. As a default setting it will start the application listenning on the `http://localhost:8090/`.

## Operation examples

For all calls it's assumed that client will provide allowed account and transfer identifiers as UUID: it's requered to generate one for creation operations and use existed one to manage of entities.

In examples only happy paths will be presented, but the service is tested to be ready for incorrect inputs and states to make a deserved response.

### Creating accounts

Let create the source account `A`:

```
curl -X POST \
  http://localhost:8090/accounts \
  -H 'Content-Type: application/json' \
  -d '{
	"account_id": "132e4367-e89b-13d5-a456-556d42440000",
	"name": "A"
}'
```

Let create the target account `B`:

```
curl -X POST \
  http://localhost:8090/accounts \
  -H 'Content-Type: application/json' \
  -d '{
	"account_id": "232e4367-e89b-13d5-a456-556d42440000",
	"name": "B"
}'
```

### Deposit money to the existing account `A`

```
curl -X POST \
  http://localhost:8090/accounts/132e4367-e89b-13d5-a456-556d42440000/deposit \
  -H 'Content-Type: application/json' \
  -d '{
	"transfer_id": "133e4147-e19b-23d3-d455-253222448010",
	"amount": 100.0
}'
```

### Transfer money from `A` to `B`.

```
curl -X POST \
  http://localhost:8090/transfers \
  -H 'Content-Type: application/json' \
  -d '{
	"transfer_id": "433e4147-e19b-23d3-d455-253222448010",
	"source_account_id": "132e4367-e89b-13d5-a456-556d42440000",
	"target_account_id": "232e4367-e89b-13d5-a456-556d42440000",
	"amount": 30.0
}'
```

As a success, you will see:

```
{
    "transfer_id": "433e4147-e19b-23d3-d455-253222448010",
    "source_account_id": "132e4367-e89b-13d5-a456-556d42440000",
    "target_account_id": "232e4367-e89b-13d5-a456-556d42440000",
    "amount": 30,
    "status": "completed"
}
```

### Retrieve an account state

Let see the state of the accouunt `A`:

```
curl -X GET \
  http://localhost:8090/accounts/132e4367-e89b-13d5-a456-556d42440000
```

Response:

```
{
    "account_id": "132e4367-e89b-13d5-a456-556d42440000",
    "name": "A",
    "balance": 70
}
```

Let see the state of the accouunt `B`:

```
curl -X GET \
  http://localhost:8090/accounts/232e4367-e89b-13d5-a456-556d42440000
```

Response:

```
{
    "account_id": "232e4367-e89b-13d5-a456-556d42440000",
    "name": "B",
    "balance": 30
}
```

As you can see 30 units was transfered from account `A` to the account `B` as we had requested.

### Retrieve a transfer state

It's also alowed to retrieve transfer state:

```
curl -X GET \
  http://localhost:8090/transfers/433e4147-e19b-23d3-d455-253222448010
```

Response:

```
{
    "transfer_id": "433e4147-e19b-23d3-d455-253222448010",
    "source_account_id": "132e4367-e89b-13d5-a456-556d42440000",
    "target_account_id": "232e4367-e89b-13d5-a456-556d42440000",
    "amount": 30,
    "status": "completed"
}
```
