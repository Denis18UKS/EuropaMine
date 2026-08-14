package com.z_mods.barotrauma.mixin;

/** Small bridge used while the client player is supported by a moving submarine frame. */
public interface ServerGamePacketListenerMotionAccess {
    void barotrauma$resetSubmarineFloatingCounters();
}
