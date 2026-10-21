package com.splitmate.split;

import java.util.List;

/**
 * Public, limited view of a split behind an invite link: enough to recognise it, nothing private
 * (first names only, no emails, no money).
 */
public record InvitePreviewResponse(String code, String splitName, String emoji, String description,
		String ownerName, int memberCount, List<PreviewMember> members, SplitStatus status, boolean joinable) {

	public record PreviewMember(String firstName, String avatarColor) {
	}

}
