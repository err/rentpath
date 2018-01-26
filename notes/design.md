# Design Notes
# Problem
Create a ranking system based on GitHub user activity.

TODO: Figure out what fundamental value this provides. Why is it useful?

# Goals
* Create an event-ingestion system for GitHub user events
* Analyze events to generate a user-score
* Create a service to publish our user-score

# Assumptions
* The facts generated for each message will be rather small (< 256k)
* This is a toy project, so I'm assuming the actual structure of a GitHub Event
  isn't a concern

# Design
<img src="./design.svg" width="512">

(Created using [Mermaid Live
Editor](https://mermaidjs.github.io/mermaid-live-editor/#/edit/Z3JhcGggVEQKUltSZXF1ZXN0XSAtLSAieyd1c2VybmFtZSc6ICd1c2VyLWEnLCAnZXZlbnRfdHlwZSc6ICdQdXNoRXZlbnQnfSIgLS0-IEhTCkhTW0h0dHAgU2VydmVyXSAtLSAiezpmYWN0cyBbLi4uXX0iIC0tPiBRW1F1ZXVlXQpRIC0tPiBEVFBbQXN5bmMgV3JpdGVyXQpEVFAgLS0-IERbRGF0b21pY10))

NOTE: Ordinarily, I would spend a fair amount of time describing the various
payloads in detail, describing the purpose of each attribute.

# Code Layout
### Prod Namespaces
* Http Server - `rentpath.scores.http-server`
* Async Writer - `rentpath.scores.datomic`

### Dev Namespaces
Since we're not yet ready to go to production, everything will be started in
development mode backed by an in-memory database.

* Dev Service (both Http Server and Async Writer) -  `rentpath.dev`
* Event Generation (for loading mock data) - `rentpath.score-events-gen`

# Discussion (Not Required Reading)
This section is only really useful if you're curious how I arrived at certain
decisions.

## Architecture
Considering the stated request is to just "return aggregate scores" for users,
my initial inclination was to back this store with DynamoDB. The stats for any
particular user doesn't affect any other user, so the data model fits.
Additionally, DynamoDB would allow us to dynamically scale costs and capacity
as our service grows.

However, if somebody asks to see, for example, the top ten users, DynamoDB will
require a table scan.

Therefore, I'm going to go ahead and back this with Datomic.

The hard constraint with Datomic is write throughput. We can mitigate this by
putting a queue in front of our ingestion engine. This will allow us to adjust
Datomic writes independent of how fast events are transmitted to us. This puts
pressure on the queuing system to hold however many events are currently
uncommitted to Datomic. SQS allows for unlimited queue size. If we decide to
use, for example, Kafka, multi-terabyte drives ought to be sufficient to hold
all in-flight messages (as the [messages will be rather small](#assumptions)).

We can still use DynamoDB as Datomic's backing storage to ensure that our costs
and capacity scales.

## Where to generate facts
We could generate facts on the Http Server or in the Datomic Transaction
Process. Generating facts on the Http Server allows us to have a
general-purpose throttle on writes to Datomic. I'm not sure of any particular
advantage to generating facts in the Datomic Transaction Process itself.

It's possible that having a general event-\>facts process might be useful. This
would mean transforming from GitHub Event-\>System Event, and sending that
System Event to a broker. However, the value proposition there is dubious at
best. There _must_ be some kind of translation from GitHub Event-\>facts. If
we're interested in generating more actions from that event, we can
asynchronously watch the Datomic log for those events. Therefore, the only
value I see is that we might be able to react to events without waiting for
them to make it to Datomic. If this is necessary, we might consider using e.g.
Kafka<sup>[1](#kafka)</sup>.

---
<a name="kafka"><sup>1</sup></a> I'm not incredibly familiar with Kafka, but my
understanding is that is supports a form of pub/sub out of the box.
