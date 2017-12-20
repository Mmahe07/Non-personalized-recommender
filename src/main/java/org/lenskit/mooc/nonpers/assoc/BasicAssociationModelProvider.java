package org.lenskit.mooc.nonpers.assoc;

import it.unimi.dsi.fastutil.longs.*;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.inject.Transient;
import org.lenskit.util.IdBox;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.io.ObjectStream;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;

/**
 * Build a model for basic association rules.  This class computes the association for all pairs of items.
 */
public class BasicAssociationModelProvider implements Provider<AssociationModel> {
    private final DataAccessObject dao;

    @Inject
    public BasicAssociationModelProvider(@Transient DataAccessObject dao) {
        this.dao = dao;
    }

    @Override
    public AssociationModel get() {
        Long2ObjectMap<LongSortedSet> itemUsers = new Long2ObjectOpenHashMap<>();
        LongSet allUsers = new LongOpenHashSet();

        // Open a stream, grouping ratings by item ID
        try (ObjectStream<IdBox<List<Rating>>> ratingStream = dao.query(Rating.class)
                .groupBy(CommonAttributes.ITEM_ID)
                .stream()) {
            // Process each item's ratings
            for (IdBox<List<Rating>> item: ratingStream) {
                // Build a set of users.  We build an array first, then convert to a set.
                LongList users = new LongArrayList();
                // Add each rating's user ID to the user sets
                for (Rating r: item.getValue()) {
                    long user = r.getUserId();
                    users.add(user);
                    allUsers.add(user);
                }
                // put this item's user set into the item user map
                // a frozen set will be very efficient later
                itemUsers.put(item.getId(), LongUtils.frozenSet(users));
            }
        }

        // Second step: compute all association rules

        // We need a map to store them
        Long2ObjectMap<Long2DoubleMap> assocMatrix = new Long2ObjectOpenHashMap<>();
        long count=0;
        // then loop over 'x' items
        for (Long2ObjectMap.Entry<LongSortedSet> xEntry: itemUsers.long2ObjectEntrySet()) {
            long xId = xEntry.getLongKey();
            LongSortedSet xUsers = xEntry.getValue();

            // set up a map to hold the scores for each 'y' item for this 'x'
            Long2DoubleMap itemScores = new Long2DoubleOpenHashMap();

            // loop over the 'y' items
            for (Long2ObjectMap.Entry<LongSortedSet> yEntry: itemUsers.long2ObjectEntrySet()) {
                long yId = yEntry.getLongKey();
                LongSortedSet yUsers = yEntry.getValue();


                for(long yUser : yUsers) {
                    if(xUsers.contains(yUser)){
                        ++count;
                    }
                }
                itemScores.put(yId,((float)count/(float)xUsers.size()));
                count=0;


            }
            assocMatrix.put(xId, itemScores);
        }
        return new AssociationModel(assocMatrix);
    }
}
