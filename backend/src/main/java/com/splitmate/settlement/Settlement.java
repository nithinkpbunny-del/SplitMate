package com.splitmate.settlement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An immutable snapshot taken each time someone presses "Calculate split". Collection {@code settlements};
 * {@code version} counts up per split (unique with the split id).
 */
@Document("settlements")
@CompoundIndex(name = "split_version", def = "{'splitId': 1, 'version': -1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
public class Settlement {

	@Id
	private ObjectId id;

	private ObjectId splitId;

	private int version;

	private ObjectId calculatedBy;

	private Instant calculatedAt;

	private String currency;

	private long totalSpendMinor;

	private int expenseCount;

	/** Exactly which expenses were included. */
	private List<ObjectId> expenseIds = new ArrayList<>();

	private List<MemberSummary> memberSummaries = new ArrayList<>();

	private List<TransferRecord> transfers = new ArrayList<>();

	public record MemberSummary(ObjectId userId, String name, String avatarColor, long paidMinor, long shareMinor,
			long paymentsSentMinor, long paymentsReceivedMinor, long netMinor) {
	}

	public record TransferRecord(ObjectId fromUserId, String fromName, ObjectId toUserId, String toName,
			long amountMinor) {
	}

}
