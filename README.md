# Tim's RentPath Candidate Exercise
## [Design Notes](./notes/design.md)

Helpful scripts:
* `bin/run-server` - Starts an in-memory, dev-mode service
* `bin/load-events` - Loads mock data into the service
* `bin/top-users` - Query the top users
* `bin/score-for-user` - Get the score for a given user-id

NOTE: You *MUST* have the [Clojure CLI
Tool](https://clojure.org/guides/getting_started) installed to use these
scripts.

### Much Needed Improvements
#### Error Handling
The system completely lacks sane error handling. The Async Writer will shut
down if it encounters an error. The Http Server doesn't return any error codes,
making it difficult for any user of the API.

Any production-grade system would require better error handling.

#### Validation
The Http Server currently just assumes every payload will contain the required
data. This is an awful assumption.

#### Better Query Endpoints
The query endpoints weren't very well thought through. In fairness, it's
difficult to assess what's appropriate without a real use case.

#### Batching in Async Writer
In theory, the Async Writer could lower its impact on Datomic by batching
requests.

#### Unit Tests
There's not a lot to unit test, but at the very least, unit testing
`rentpath.scores.http-server/event->facts` would help develop some much needed
validation.
