package com.splitmate.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Money one member paid that is shared by some or all members. Collection {@code expenses}.
 * Deleting is soft ({@code deleted=true}); deleted expenses are excluded from every calculation.
 */
@Document("expenses")
@CompoundIndexes({
		@CompoundIndex(name = "split_date", def = "{'splitId': 1, 'date': -1}"),
		@CompoundIndex(name = "split_deleted", def = "{'splitId': 1, 'deleted': 1}"),
		@CompoundIndex(name = "split_paidBy", def = "{'splitId': 1, 'paidBy': 1}") })
@Getter
@Setter
@NoArgsConstructor
public class Expense {

	@Id
	private ObjectId id;

	private ObjectId splitId;

	private String description;

	private long amountMinor;

	private String currency;

	private ObjectId categoryId;

	private ObjectId paidBy;

	private SplitType splitType;

	private List<ObjectId> participants = new ArrayList<>();

	private List<Share> shares = new ArrayList<>();

	private LocalDate date;

	private String notes;

	private ObjectId createdBy;

	private boolean deleted;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

}
