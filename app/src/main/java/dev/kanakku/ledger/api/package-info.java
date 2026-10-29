/**
 * The ledger's public face, and the only way into its tables: post to the journal (keyed, with
 * reverse and replace for deletes and edits), read history and balances scoped to the caller, write
 * a balance off, merge a guest into a member. No REST endpoint; feature modules call these.
 */
@NamedInterface("api")
package dev.kanakku.ledger.api;

import org.springframework.modulith.NamedInterface;
