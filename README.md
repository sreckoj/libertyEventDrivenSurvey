# libertyEventDrivenSurvey

`libertyEventDrivenSurvey` is an example event-driven survey application demonstrating [Liberty InstantOn](https://openliberty.io/docs/latest/instanton.html), CloudEvents, KNative, and MicroProfile Reactive Messaging 3. Architecture diagram for the location geocoding example:

![Architecture diagram](doc/libertyEventDrivenSurvey-location.png)

## Development

1. If using `podman machine`:
    1. Set your connection to the `root` connection:
       ```
       podman system connection default podman-machine-default-root
       ```
    1. If the machine has SELinux `virt_sandbox_use_netlink` disabled (i.e. the following returns `off`):
       ```
       podman machine ssh "getsebool virt_sandbox_use_netlink"
       ```
       Then, enable it:
       ```
       podman machine ssh "setsebool virt_sandbox_use_netlink 1"
       ```
       Note that this must be done after every time the podman machine restarts.
1. Build:
   ```
   mvn clean deploy
   ```

### Deploy to OpenShift

#### Pre-requisities

1. Ensure the [internal registry is available](https://publib.boulder.ibm.com/httpserv/cookbook/Troubleshooting_Recipes-Troubleshooting_OpenShift_Recipes-OpenShift_Use_Image_Registry_Recipe.html)

#### Deploy surveyInputService

1. Push `surveyInputService` to the registry:
   ```
   REGISTRY=$(oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}')
   echo "Registry host: ${REGISTRY}"
   printf "Does it look good (yes=ENTER, no=Ctrl^C)? "
   read trash
   podman login --tls-verify=false -u $(oc whoami) -p $(oc whoami -t) ${REGISTRY}
   podman tag localhost/surveyinputservice $REGISTRY/libertysurvey/surveyinputservice
   podman push --tls-verify=false $REGISTRY/libertysurvey/surveyinputservice
   ```
1. Check the current project is the right one:
   ```
   oc project
   ```
1. Create a KNative Service for `surveyInputService` replacing the `kafka.bootstrap.servers` envar value with the AMQ Streams Kafka Cluster bootstrap address:
   ```
   apiVersion: serving.knative.dev/v1
   kind: Service
   metadata:
     name: surveyinputservice
   spec:
     template:
       spec:
         containers:
         - name: surveyinputservice
           image: image-registry.openshift-image-registry.svc:5000/libertysurvey/surveyinputservice
           imagePullPolicy: Always
           env:
           - name: kafka.bootstrap.servers
             value: my-cluster-kafka-bootstrap.amq-streams-kafka.svc:9092
         timeoutSeconds: 300
   ```
   Apply:
   ```
   oc apply -f doc/example_surveyinputservice.yaml
   ```
1. Query until `READY` is `True`:
   ```
   kn service list surveyinputservice
   ```
1. Access the URL to drive pod creation:
   ```
   curl -k "$(kn service list surveyinputservice -o jsonpath="{.items[0].status.url}{'\n'}")"
   ```
1. Double check logs look good:
   ```
   oc exec -it $(oc get pod -o name | grep surveyinputservice) -c surveyinputservice -- cat /logs/messages.log
   ```

#### Deploy surveyGeocoderService

1. Push `surveyGeocoderService` to the registry:
   ```
   REGISTRY=$(oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}')
   echo "Registry host: ${REGISTRY}"
   printf "Does it look good (yes=ENTER, no=Ctrl^C)? "
   read trash
   podman login --tls-verify=false -u $(oc whoami) -p $(oc whoami -t) ${REGISTRY}
   podman tag localhost/surveygeocoderservice $REGISTRY/libertysurvey/surveygeocoderservice
   podman push --tls-verify=false $REGISTRY/libertysurvey/surveygeocoderservice
   ```
1. Create a KNative Service for `surveyGeocoderService` replacing `INSERT_API_KEY` with your Google Maps API key and `kafka.bootstrap.servers` envar value with the AMQ Streams Kafka Cluster bootstrap address:
   ```
   apiVersion: serving.knative.dev/v1
   kind: Service
   metadata:
     name: surveygeocoderservice
   spec:
     template:
       spec:
         containers:
         - name: surveygeocoderservice
           image: image-registry.openshift-image-registry.svc:5000/libertysurvey/surveygeocoderservice
           imagePullPolicy: Always
           env:
           - name: kafka.bootstrap.servers
             value: my-cluster-kafka-bootstrap.amq-streams-kafka.svc:9092
           - name: GOOGLE_API_KEY
             value: INSERT_API_KEY
         timeoutSeconds: 300
   ```
   Apply:
   ```
   oc apply -f doc/example_surveygeocoderservice.yaml
   ```
1. Query until `READY` is `True`:
   ```
   kn service list surveygeocoderservice
   ```
1. Access the URL to drive pod creation:
   ```
   curl -k "$(kn service list surveygeocoderservice -o jsonpath="{.items[0].status.url}{'\n'}")"
   ```
1. Double check logs look good:
   ```
   oc exec -it $(oc get pod -o name | grep surveygeocoderservice) -c surveygeocoderservice -- cat /logs/messages.log
   ```
1. Create a KNative Eventing KafkaSource for `surveyGeocoderService` replacing `bootstrapServers` with the AMQ Streams Kafka Cluster bootstrap address:
   ```
   apiVersion: sources.knative.dev/v1beta1
   kind: KafkaSource
   metadata:
     name: locationtopicsource
   spec:
     bootstrapServers:
     - my-cluster-kafka-bootstrap.amq-streams-kafka.svc:9092
     sink:
       ref:
         apiVersion: serving.knative.dev/v1
         kind: Service
         name: surveygeocoderservice
       uri: "/api/cloudevents/locationInput"
     topics:
     - locationtopic
   ```
   Apply:
   ```
   oc apply -f doc/example_surveygeocoderkafkasource.yaml
   ```
1. Query until `OK` is `++`:
   ```
   kn source kafka describe locationtopicsource
   ```

#### Test

1. Submit a location input:
    1. Using the command line:
       ```
       curl -k --data "textInput1=New York, NY" "$(kn service list surveyinputservice -o jsonpath="{.items[0].status.url}{'\n'}")/LocationSurvey"
       ```
    1. Using the browser:
        1. Find and open the URL:
           ```
           kn service list surveyinputservice -o jsonpath="{.items[0].status.url}{'\n'}"
           ```
    1. Click `Location Survey` and submit the form
1. Double check logs look good:
   ```
   oc exec -it $(oc get pod -o name | grep surveygeocoderservice) -c surveygeocoderservice -- tail -f /logs/messages.log
   ```

#### Clean-up tasks

##### Delete surveyInputService

```
kn service delete surveyinputservice
```

##### Delete surveyGeocoderService

1. Delete the KafkaSource:
   ```
   kn source kafka delete locationtopicsource
   ```
1. Delete the KNative Service:
   ```
   kn service delete surveygeocoderservice
   ```

### Testing Locally

1. Create Kafka container network if it doesn't exist:
   ```
   podman network create kafka
   ```
1. Start Kafka:
   ```
   podman run --rm -p 9092:9092 -e "ALLOW_PLAINTEXT_LISTENER=yes" -e "KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka-0:9092" -e "KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093" -e "KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT" -e "KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@kafka-0:9093" -e "KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER" -e "KAFKA_CFG_PROCESS_ROLES=controller,broker" -e "KAFKA_CFG_NODE_ID=0" --name kafka-0 --network kafka docker.io/bitnami/kafka
   ```
1. Run `surveyInputService`:
   ```
   podman run --privileged --rm --network kafka  --rm -p 8080:8080 -p 8443:8443 -it localhost/surveyinputservice:latest
   ```
1. Wait for the message:
   ```
   [...] CWWKZ0001I: Application surveyInputService started [...]
   ```
1. Access <http://localhost:8080/location.html> or <https://localhost:8443/location.html>

### Additional Development Notes

#### Simple tests of the Geocoder Service

1. Run `surveyGeocoderService`:
   ```
   podman run --privileged --rm --rm -p 8080:8080 -p 8443:8443 -it localhost/surveygeocoderservice:latest
   ```
1. To post a [`CloudEvent`](https://github.com/cloudevents/spec/blob/v1.0/spec.md#required-attributes):
   ```
   curl -X POST http://localhost:8080/api/cloudevents/locationInput \
     -H "Ce-Source: https://example.com/" \
     -H "Ce-Id: $(uuidgen)" \
     -H "Ce-Specversion: 1.0" \
     -H "Ce-Type: CloudEvent1" \
     -H "Content-Type: application/json" \
     -d "\"Hello World\""
   ```

## Learn More

1. <https://developer.ibm.com/articles/develop-reactive-microservices-with-microprofile/>
1. <https://openliberty.io/guides/microprofile-reactive-messaging.html>
1. <https://smallrye.io/smallrye-reactive-messaging/latest/concepts/concepts/>
1. <https://openliberty.io/blog/2022/10/17/microprofile-serverless-ibm-code-engine.html>
1. <https://github.com/OpenLiberty/open-liberty/issues/19889>
1. <https://github.com/OpenLiberty/open-liberty/issues/21659>
