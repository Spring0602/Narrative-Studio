package edu.njust.narrativestudio.service;

/** Optional external text generation. Implementations must not persist authoring data. */
public interface DialogueProvider {
    String generate(String context);
}
