package org.obc.core.net.message;

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.obc.core.capsule.TransactionCapsule;
import org.obc.core.net.message.MessageTypes;
import org.obc.protos.Protocol.Block;
import org.obc.protos.Protocol.Items;

public class BlocksMessage extends obcMessage {

  private List<Block> blocks;

  public BlocksMessage(byte[] data) throws Exception {
    super(data);
    this.type = MessageTypes.BLOCKS.asByte();
    Items items = Items.parseFrom(getCodedInputStream(data));
    if (items.getType() == Items.ItemType.BLOCK) {
      blocks = items.getBlocksList();
    }
    if (isFilter() && CollectionUtils.isNotEmpty(blocks)) {
      compareBytes(data, items.toByteArray());
      for (Block block : blocks) {
        TransactionCapsule.validContractProto(block.getTransactionsList());
      }
    }
  }

  public List<Block> getBlocks() {
    return blocks;
  }

  @Override
  public String toString() {
    return super.toString() + "size: " + (CollectionUtils.isNotEmpty(blocks) ? blocks
        .size() : 0);
  }

  @Override
  public Class<?> getAnswerMessage() {
    return null;
  }

}
