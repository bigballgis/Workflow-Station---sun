package com.portal.component;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Frozen before Engine accepts completion; replay never re-reads later workflow variables. */
record TaskApprovalPortalValues(ObjectNode baseline,ObjectNode accepted,String catalogId) { }
