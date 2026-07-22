import os
import re

entity_comments = {
    "Auction.java": "경매 정보",
    "Bid.java": "경매 입찰 내역",
    "ChatRoom.java": "채팅방",
    "ChatMessage.java": "채팅 메시지",
    "User.java": "사용자 정보",
    "EmailVerificationCode.java": "이메일 인증 코드",
    "Review.java": "사용자 매너 리뷰",
    "Trade.java": "상품 거래 내역",
    "Notification.java": "사용자 알림 내역",
    "Comment.java": "상품 문의/댓글",
    "Product.java": "판매/경매 상품 정보",
    "ProductImage.java": "상품 이미지",
    "Wishlist.java": "사용자 관심 상품(찜)"
}

def process_file(filepath):
    filename = os.path.basename(filepath)
    if filename not in entity_comments or entity_comments[filename] is None:
        return
    
    with open(filepath, 'r') as f:
        content = f.read()
    
    if '@Comment' in content:
        return
    
    comment_text = entity_comments[filename]
    
    if '@Entity' in content:
        new_content = content.replace('@Entity', f'@Entity\n@Comment("{comment_text}")', 1)
        with open(filepath, 'w') as f:
            f.write(new_content)

for root, _, files in os.walk('backend/src/main/java/com/nplohs/market'):
    for file in files:
        if file.endswith('.java'):
            process_file(os.path.join(root, file))
