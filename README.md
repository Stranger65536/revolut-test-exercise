## RESTful API (including data model and the backing implementation) for money transfers between accounts

Uses H2DB database with default data of 2 accounts:

| ID | Amount |
|---|---|
| 1 | 2 |
| 100.0 | 200.0 |

## REST API Definition

```
GET /accounts/:id - returns information about current amount for the specified account id
```

| Response status | Response content | Description |
|---|---|---|
| 200 | `{"id": String, "amount": String}` | Account exists, success |
| 404 | `{"error": String}` | Account with specified does not exist |
| 500 | `{"error": String, "frames": [String]}` | Error has occurred |

```
POST /transfers - makes a transfer of the specifide amount between specifiede accounts
{
  "sourceAccountId": String,
  "targetAccountId": String,
  "amount": String
} 
```

| Response status | Response content | Description |
|---|---|---|
| 204 | `No content` | Transfer successful |
| 404 | `{"error": String}` | One of the specified accounts doesn't exist, `error` field contains the details of which one is missing|
| 400 | `{"error": String, "frames": [String]}` | Either request JSON is invalid or doesn't match expected schema, or source and target accounts are the same, or specified amount is less than or equal to zero |
| 403 | `{"error": String}` | Transfer can't be processed due to insufficient funds on the source account |
| 500 | `{"error": String, "frames": [String]}` | Error has occurred |

## Start

This application requires JDK 8 and gradle 5+

```
gradle clean build
java -jar build/libs/revolut-1.0.jar
```

### Properties

These properties are configurable at `application.properties` to modify startup port and database connection details:

```
db.classname
db.connectionTestString
db.password
db.url
db.user
server.port
```


