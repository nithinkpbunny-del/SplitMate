package com.splitmate.payment;

import java.time.Instant;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A real-world "I paid you back" transfer between two members. Collection {@code payments}.
 */
@Document("payments")
@CompoundIndex(name = "split_created", def = "{'splitId': 1, 'createdAt': -1}")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

	@Id
	private ObjectId id;

	private ObjectId splitId;

	private ObjectId fromUserId;

	private ObjectId toUserId;

	private long amountMinor;

	private String note;

	/** The settlement snapshot the payer was looking at, if any. */
	private ObjectId settlementId;

	private ObjectId recordedBy;

	@CreatedDate
	private Instant createdAt;

}
