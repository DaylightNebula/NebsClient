package dsh.nebsclient.api

/** A command failed: the client rejected it, couldn't be reached, or didn't become ready in time. */
public class NebsException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
