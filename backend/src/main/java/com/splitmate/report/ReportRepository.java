package com.splitmate.report;

import java.util.List;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReportRepository extends MongoRepository<Report, ObjectId> {

	List<Report> findBySplitIdOrderByGeneratedAtDesc(ObjectId splitId);

	void deleteBySplitId(ObjectId splitId);

}
