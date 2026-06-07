package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SpendingBreakdownData(
    val totalAmount: String,
    val cardTxnCount: Int,
    val cashTxnCount: Int,
    val creditCardAmount: String,
    val creditCardTxns: Int,
    val bankAmount: String,
    val bankTxns: Int,
    val cashAmount: String,
    val cashTxns: Int,
    val footerNote: String
)

@Composable
fun SpendingBreakdownTile(
    data: SpendingBreakdownData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF1C1C1E),
                shape = RoundedCornerShape(20.dp)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(18.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SPENDING",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E8E93),
                letterSpacing = (0.08f).sp
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge 1: Credit card
                Badge(
                    icon = Icons.Outlined.CreditCard,
                    text = "${data.cardTxnCount} TXNS"
                )
                
                // Badge 2: Cash
                Badge(
                    icon = Icons.Outlined.Wallet,
                    text = "${data.cashTxnCount} TXNS"
                )
            }
        }
        
        // Total Amount
        Text(
            text = data.totalAmount,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFFFFF),
            letterSpacing = (-0.5f).sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 14.dp)
        )
        
        // Divider
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF2C2C2E))
        )
        
        // Breakdown Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Column 1: Credit card
            BreakdownColumn(
                icon = Icons.Outlined.CreditCard,
                label = "Credit card",
                amount = data.creditCardAmount,
                subText = "${data.creditCardTxns} txns",
                modifier = Modifier.weight(1f)
            )
            
            // Vertical Divider
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF2C2C2E))
            )
            
            // Column 2: Bank account
            BreakdownColumn(
                icon = Icons.Outlined.AccountBalance,
                label = "Bank account",
                amount = data.bankAmount,
                subText = "${data.bankTxns} txns",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            
            // Vertical Divider
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF2C2C2E))
            )
            
            // Column 3: Cash
            BreakdownColumn(
                icon = Icons.Outlined.Wallet,
                label = "Cash",
                amount = data.cashAmount,
                subText = "${data.cashTxns} txns",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
        
        // Footer
        Text(
            text = data.footerNote,
            fontSize = 11.sp,
            color = Color(0xFF636366),
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun Badge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = Color(0xFF2C2C2E),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFFFFFF).copy(alpha = 0.75f),
            modifier = Modifier
                .width(12.dp)
                .height(12.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFFFFFFF)
        )
    }
}

@Composable
private fun BreakdownColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    subText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Label row
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF8E8E93).copy(alpha = 0.7f),
                modifier = Modifier
                    .width(11.dp)
                    .height(11.dp)
            )
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Amount
        Text(
            text = amount,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFFFFFF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Sub-text
        Text(
            text = subText,
            fontSize = 10.5.sp,
            color = Color(0xFF636366),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(
    backgroundColor = 0xFF000000,
    showBackground = true
)
@Composable
private fun SpendingBreakdownTilePreview() {
    SpendingBreakdownTile(
        data = SpendingBreakdownData(
            totalAmount = "₹22,542.21",
            cardTxnCount = 29,
            cashTxnCount = 3,
            creditCardAmount = "₹1,591.69",
            creditCardTxns = 4,
            bankAmount = "₹17,647.52",
            bankTxns = 25,
            cashAmount = "₹3,303",
            cashTxns = 3,
            footerNote = "Cash is 14% of spend · excl. investments"
        )
    )
}
