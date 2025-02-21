package net.william278.husktowns.events;

import org.jetbrains.annotations.NotNull;

/**
 * An event fired when a town is created
 */
public interface ITownCreateEvent extends OnlineUserEvent {

    /**
     * Get the name of the town to be created
     *
     * @return the name of the town to be created
     */
    @NotNull
    String getTownName();

    /**
     * Set the name of the town to be created
     *
     * @param townName the name of the town to be created
     */
    void setTownName(@NotNull String townName);

}
