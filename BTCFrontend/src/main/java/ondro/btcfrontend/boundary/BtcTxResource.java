package ondro.btcfrontend.boundary;

import io.reactivex.Flowable;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;
import ondro.btcdataproducer.util.Logging;
import ondro.btcfrontend.kafkaconsumer.BtcTx;
import org.reactivestreams.Publisher;

/**
 *
 * @author Ondrej Mihalyi
 */
@Path("btctx")
@ApplicationScoped
public class BtcTxResource {

    @Inject @BtcTx
    private Publisher<String> btcTransactions;

    private SseBroadcaster btcTxBroadcaster;

    @Context
    private void setSse(Sse sse) {
        this.btcTxBroadcaster = sse.newBroadcaster();
        OutboundSseEvent.Builder eventBuilder = sse.newEventBuilder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE);
        Flowable.fromPublisher(btcTransactions)
                .doOnNext(data -> {
                    Logging.of(this).info("Sending event: " + data);
                })
                .map(data -> {
                    return eventBuilder
                            .data(data)
                            .build();
                })
                .doOnError(e -> {
                    Logging.of(this).warning(e.getMessage());
                })
                .doFinally(this.btcTxBroadcaster::close)
                .subscribe(this.btcTxBroadcaster::broadcast);
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void getBtcTransactions(
            @Context SseEventSink eventSink,
            @Context Sse sse) {
        btcTxBroadcaster.register(eventSink);
    }
}
