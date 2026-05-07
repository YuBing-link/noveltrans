package com.yumu.noveltranslator.port.in;

import com.stripe.model.Event;

public interface WebhookPort {
    String processEvent(Event event);
}
