package ondro.btcfrontend.boundary;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import ondro.btcfrontend.Resource;
import ondro.btcfrontend.entity.BitstampTicker;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvoker;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvokerProvider;

/**
 * REST Web Service
 *
 * @author Ondrej Mihalyi
 */
@Path("rate")
@RequestScoped
public class RateResource {

    @Inject
    @ConfigProperty(name = "bitstamp.ticker.url",
            defaultValue = "https://www.bitstamp.net/api/ticker/")
    private URL tickerUrl;

    @Inject
    @ConfigProperty(defaultValue = "10000")
    private long timeoutInMillis;
    
    @Inject
    @Resource
    private Scheduler scheduler;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 100, delay = 1, delayUnit = ChronoUnit.SECONDS)
    public String getRateFromBackendSync() throws URISyntaxException {
        BitstampTicker ticker = ClientBuilder.newClient()
                .target(tickerUrl.toURI())
                .request()
                .get(BitstampTicker.class);
        return String.valueOf(ticker.getLast());
    }
    
//    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public void getRateFromBackendAsyncEE7(@Suspended AsyncResponse response)
            throws URISyntaxException {
        Flowable<BitstampTicker> fTicker = Flowable.create(emitter -> {
            ClientBuilder.newClient()
                .target(tickerUrl.toURI())
                .request()
                .async()
                .get(new InvocationCallback<BitstampTicker>() {
                    @Override
                    public void completed(BitstampTicker response) {
                        emitter.onNext(response);
                        emitter.onComplete();
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        emitter.onError(throwable);
                    }
                });
        }, BackpressureStrategy.DROP);
        fTicker = fTicker
                .subscribeOn(scheduler)
                .observeOn(scheduler);
        
        fTicker.timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
            .map(
                    ticker -> String.valueOf(ticker.getLast()))
            .doOnNext(rate -> {
                response.resume(rate);
            })
            .doOnError(e -> {
                response.resume(e);
            })
            .subscribe();
    }

//    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public void getRateFromBackendAsyncEE8(@Suspended AsyncResponse response)
            throws URISyntaxException {
        ClientBuilder.newClient()
            .register(RxFlowableInvokerProvider.class)
            .target(tickerUrl.toURI())
            .request()
            .rx(RxFlowableInvoker.class)
            .get(BitstampTicker.class)
            .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
            .map(
                    ticker -> String.valueOf(ticker.getLast()))
            .doOnNext(rate -> {
                response.resume(rate);
            })
            .doOnError(e -> {
                response.resume(e);
            })
            .subscribe();
    }

}
