// Clan designs are live website publications; game delivery is enabled separately.
export async function clanKitCatalogue(db) {
  if (!db) return [];
  const {results} = await db.prepare(`SELECT k.id,k.name,k.color,k.lore_json,k.version,k.shop_price_cents,c.name AS clan_name,a.username AS owner_name
    FROM site_clan_kits k JOIN site_clans c ON c.id=k.clan_id AND c.active=1
    JOIN site_accounts a ON a.id=c.owner_id ORDER BY c.name,k.id LIMIT 500`).all();
  return results.map(row => ({listingId:row.id,listingVersion:Number(row.version),blueprintVersion:Number(row.version),
    priceCents:Number(row.shop_price_cents),name:row.name,sellerName:row.clan_name,ownerName:row.owner_name,
    quality:'uncommon',commerceKind:'personal_sale',shopType:'clan',purchaseAvailable:false,
    completedSales:0,color:row.color,lore:JSON.parse(row.lore_json)}));
}
